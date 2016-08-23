/*
 * Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Caddisfly
 *
 * Akvo Caddisfly is free software: you can redistribute it and modify it under the terms of
 * the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 * either version 3 of the License or any later version.
 *
 * Akvo Caddisfly is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License included below for more details.
 *
 * The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.caddisfly.sensor.colorimetry.strip.result;

import android.graphics.Bitmap;

import org.akvo.caddisfly.sensor.colorimetry.strip.calibration.CalibrationCard;
import org.akvo.caddisfly.sensor.colorimetry.strip.model.ColorDetected;
import org.akvo.caddisfly.sensor.colorimetry.strip.model.StripTest;
import org.akvo.caddisfly.sensor.colorimetry.strip.util.Constant;
import org.akvo.caddisfly.sensor.colorimetry.strip.util.FileStorage;
import org.akvo.caddisfly.sensor.colorimetry.strip.util.OpenCVUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Created by markwestra on 18/02/16
 */
class ResultUtil {
    private static final int INTERPOLATION_NUMBER = 10;
    private static final Scalar labWhite = new Scalar(255, 128, 128);
    private static final Scalar labGrey = new Scalar(128, 128, 128);
    private static final Scalar labBlack = new Scalar(0, 128, 128);
    private static final double yColorRect = 20d; //distance from top Mat to top color rectangles
    private static final int circleRadius = 10;
    private static final double xMargin = 10d;

    public static Mat getMatFromFile(FileStorage fileStorage, int imageNo) {
        //if in DetectStripTask, no strip was found, an image was saved with the String Constant.ERROR
        boolean isInvalidStrip = fileStorage.checkIfFilenameContainsString(Constant.STRIP + imageNo + Constant.ERROR);

        String error = isInvalidStrip ? Constant.ERROR : "";

        // read the Mat object from internal storage
        byte[] data;
        try {
            data = fileStorage.readByteArray(Constant.STRIP + imageNo + error);

            if (data != null) {
                // determine cols and rows dimensions
                byte[] rows = new byte[4];
                byte[] cols = new byte[4];

                int length = data.length;
                System.arraycopy(data, length - 8, rows, 0, 4);
                System.arraycopy(data, length - 4, cols, 0, 4);

                int rowsNum = FileStorage.byteArrayToLeInt(rows);
                int colsNum = FileStorage.byteArrayToLeInt(cols);

                // remove last part
                byte[] imgData = Arrays.copyOfRange(data, 0, data.length - 8);

                // reserve Mat of proper size:
                Mat result = new Mat(rowsNum, colsNum, CvType.CV_8UC3);

                // put image data back in Mat:
                result.put(0, 0, imgData);
                return result;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Bitmap makeBitmap(Mat mat) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bitmap);

            double max = bitmap.getHeight() > bitmap.getWidth() ? bitmap.getHeight() : bitmap.getWidth();
            double min = bitmap.getHeight() < bitmap.getWidth() ? bitmap.getHeight() : bitmap.getWidth();
            double ratio = min / max;
            int width = (int) Math.max(400, max);
            int height = (int) Math.round(ratio * width);

            return Bitmap.createScaledBitmap(bitmap, width, height, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Mat createStripMat(Mat mat, int borderSize, Point centerPatch, boolean grouped) {
        //done with lab schema, make rgb to show in image view
        // mat holds the strip image
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_Lab2RGB);

        //extend the strip with a border, so we can draw a circle around each patch that is
        //wider than the strip itself. That is just because it looks nice.


        Core.copyMakeBorder(mat, mat, borderSize, borderSize, borderSize, borderSize, Core.BORDER_CONSTANT, new Scalar(255, 255, 255, 255));

        // Draw a green circle at a particular location patch
        // only draw if this is not a 'grouped' strip
        if (!grouped) {
            Imgproc.circle(mat, new Point(centerPatch.x + borderSize, mat.height() / 2), (int) Math.ceil(mat.height() * 0.4),
                    new Scalar(0, 255, 0, 255), 2);
        }
        return mat;
    }

    public static Mat createDescriptionMat(String desc, int width) {
        int[] baseline = new int[1];
        Size textSizeDesc = Imgproc.getTextSize(desc, Core.FONT_HERSHEY_SIMPLEX, 0.35d, 1, baseline);
        Mat descMat = new Mat((int) Math.ceil(textSizeDesc.height) * 3, width, CvType.CV_8UC3, labWhite);
        Imgproc.putText(descMat, desc, new Point(2, descMat.height() - textSizeDesc.height), Core.FONT_HERSHEY_SIMPLEX, 0.35d, labBlack, 1, Core.LINE_8, false);

        return descMat;
    }

    /*
      * COLOR RANGE AS IN JSON FILE (FROM MANUFACTURER)
      * Create Mat to hold a rectangle for each color
      * the corresponding value written as text above that rectangle
      */
    public static Mat createColourRangeMatSingle(List<StripTest.Brand.Patch> patches, int patchNum, int width, double xTranslate) {
        // horizontal size of mat: width
        // vertical size of mat: size of colour block - xMargin + top distance
        Mat colorRangeMat = new Mat((int) Math.ceil(xTranslate - xMargin + yColorRect), width, CvType.CV_8UC3, labWhite);
        JSONArray colours;
        colours = patches.get(patchNum).getColours();

        for (int d = 0; d < colours.length(); d++) {
            try {

                JSONObject colourObj = colours.getJSONObject(d);

                double value = colourObj.getDouble("value");
                JSONArray lab = colourObj.getJSONArray("lab");
                Scalar scalarLab = new Scalar((lab.getDouble(0) / 100) * 255, lab.getDouble(1) + 128, lab.getDouble(2) + 128);
                Size textSizeValue = Imgproc.getTextSize(roundAxis(value), Core.FONT_HERSHEY_SIMPLEX, 0.3d, 1, null);

                //draw a rectangle filled with color for result value
                Point topLeft = new Point(xTranslate * d, yColorRect);
                Point bottomRight = new Point(topLeft.x + xTranslate - xMargin, yColorRect + xTranslate);
                Imgproc.rectangle(colorRangeMat, topLeft, bottomRight, scalarLab, -1);

                //draw color value above rectangle
                Point centerText = new Point(topLeft.x + (bottomRight.x - topLeft.x) / 2 - textSizeValue.width / 2, yColorRect - textSizeValue.height);
                Imgproc.putText(colorRangeMat, roundAxis(value), centerText, Core.FONT_HERSHEY_SIMPLEX, 0.3d, labGrey, 1, Core.LINE_AA, false);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return colorRangeMat;
    }

    /*
  * COLOR RANGE AS IN JSON FILE (FROM MANUFACTURER)
  * Create Mat to hold a rectangle for each color
  * the corresponding value written as text above that rectangle
  */
    public static Mat createColourRangeMatGroup(List<StripTest.Brand.Patch> patches, int width, double xTranslate) {
        // horizontal size of mat: width
        // vertical size of mat: size of colour block - xMargin + top distance

        int numPatches = patches.size();
        Mat colorRangeMat = new Mat((int) Math.ceil(numPatches * (xTranslate + xMargin) - xMargin + yColorRect), width, CvType.CV_8UC3, labWhite);

        JSONArray colours;
        int offset = 0;
        System.out.println("*** number of patches:" + numPatches);
        for (int p = 0; p < numPatches; p++) {
            colours = patches.get(p).getColours();
            for (int d = 0; d < colours.length(); d++) {
                try {

                    JSONObject colourObj = colours.getJSONObject(d);

                    double value = colourObj.getDouble("value");
                    JSONArray lab = colourObj.getJSONArray("lab");
                    Scalar scalarLab = new Scalar((lab.getDouble(0) / 100) * 255, lab.getDouble(1) + 128, lab.getDouble(2) + 128);
                    Size textSizeValue = Imgproc.getTextSize(roundAxis(value), Core.FONT_HERSHEY_SIMPLEX, 0.3d, 1, null);

                    //draw a rectangle filled with color for result value
                    Point topLeft = new Point(xTranslate * d, yColorRect + offset);
                    Point bottomRight = new Point(topLeft.x + xTranslate - xMargin, yColorRect + xTranslate + offset);
                    Imgproc.rectangle(colorRangeMat, topLeft, bottomRight, scalarLab, -1);

                    //draw color value above rectangle
                    if (p == 0) {
                        Point centerText = new Point(topLeft.x + (bottomRight.x - topLeft.x) / 2 - textSizeValue.width / 2, yColorRect - textSizeValue.height);
                        Imgproc.putText(colorRangeMat, roundAxis(value), centerText, Core.FONT_HERSHEY_SIMPLEX, 0.3d, labGrey, 1, Core.LINE_AA, false);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            offset += xTranslate + xMargin;
        }
        return colorRangeMat;
    }

    /*
       * VALUE MEASURED
       * Create Mat to hold a line between min and max values, on it a circle filled with
       * the color detected below which the result value measured
       */
    public static Mat createValueMeasuredMatSingle(JSONArray colours, double resultValue, ColorDetected colorDetected, int width, double xTranslate) {
        Mat valueMeasuredMat = new Mat(50, width, CvType.CV_8UC3, labWhite);
        JSONObject colourObj;
        JSONObject nextColourObj;
        boolean resultIsDrawn = false;

        //grey line with result values at left and right
        Imgproc.line(valueMeasuredMat, new Point(xMargin, 25), new Point(valueMeasuredMat.cols() - 2 * xMargin, 25), labGrey, 1, Core.LINE_AA, 0);

        //get values for lowest and highest result values from striptest range
        double leftValue;
        try {
            leftValue = colours.getJSONObject(0).getDouble("value");

            double rightValue = colours.getJSONObject(colours.length() - 1).getDouble("value");
            Size textSizeLeftValue = Imgproc.getTextSize(String.format(Locale.getDefault(), "%.0f", leftValue), Core.FONT_HERSHEY_SIMPLEX, 0.3d, 1, null);
            Size textSizeRightValue = Imgproc.getTextSize(String.format(Locale.getDefault(), "%.0f", rightValue), Core.FONT_HERSHEY_SIMPLEX, 0.3d, 1, null);

            Imgproc.putText(valueMeasuredMat, String.format(Locale.getDefault(), "%.0f", leftValue),
                    new Point((xTranslate - xMargin) / 2 - textSizeLeftValue.width / 2, 15), Core.FONT_HERSHEY_SIMPLEX,
                    0.3d, labGrey, 1, Core.LINE_AA, false);
            Imgproc.putText(valueMeasuredMat, String.format(Locale.getDefault(), "%.0f", rightValue),
                    new Point(valueMeasuredMat.cols() - xMargin - (xTranslate - xMargin) / 2 - textSizeRightValue.width / 2, 15),
                    Core.FONT_HERSHEY_SIMPLEX, 0.3d, labGrey, 1, Core.LINE_AA, false);


            // we need to iterate over the result values to determine where the circle should be placed
            for (int d = 0; d < colours.length(); d++) {
                colourObj = colours.getJSONObject(d);
                if (d < colours.length() - 1) {
                    nextColourObj = colours.getJSONObject(d + 1);
                } else nextColourObj = colourObj;

                double value = colourObj.getDouble("value");
                double nextValue = nextColourObj.getDouble("value");

                if (resultValue <= nextValue && !resultIsDrawn) {

                    //calculate the amount above the lowest value
                    //calculate number of pixels needed to translate in x direction
                    double transX = xTranslate * ((resultValue - value) / (nextValue - value));

                    Scalar resultColor = colorDetected.getLab();
                    //calculate where the center of the circle should be
                    double left = xTranslate * d;
                    double right = left + xTranslate - xMargin;
                    Point centerCircle = (transX) + xTranslate * d < xMargin ? new Point(xMargin, 25d) :
                            new Point(left + (right - left) / 2 + transX, 25d);

                    //get text size of value test
                    Size textSize = Imgproc.getTextSize(roundResult(resultValue), Core.FONT_HERSHEY_SIMPLEX, 0.35d, 1, null);

                    Imgproc.circle(valueMeasuredMat, centerCircle, circleRadius, resultColor, -1, Imgproc.LINE_AA, 0);
                    Imgproc.putText(valueMeasuredMat, roundResult(resultValue), new Point(centerCircle.x - textSize.width / 2, 45d),
                            Core.FONT_HERSHEY_SIMPLEX, 0.35d, labBlack, 1, Core.LINE_AA, false);

                    resultIsDrawn = true;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return valueMeasuredMat;
    }

    /*
       * VALUE MEASURED
       * Create Mat to hold a line between min and max values, on it a circle filled with
       * the color detected below which the result value measured
       */
    public static Mat createValueMeasuredMatGroup(JSONArray colours, double result, ColorDetected[] colorsDetected, int width, double xTranslate) {
        int size = 50 + colorsDetected.length * (2 * circleRadius + 5);
        Mat valueMeasuredMat = new Mat(size, width, CvType.CV_8UC3, labWhite);

        JSONObject colourObj;
        JSONObject nextColourObj;
        boolean resultIsDrawn = false;

        //grey line with result values at left and right
        Imgproc.line(valueMeasuredMat, new Point(xMargin, 25), new Point(valueMeasuredMat.cols() - 2 * xMargin, 25), labGrey, 1, Core.LINE_AA, 0);

        //get values for lowest and highest result values from striptest range
        double leftValue;
        try {
            leftValue = colours.getJSONObject(0).getDouble("value");

            double rightValue = colours.getJSONObject(colours.length() - 1).getDouble("value");
            Size textSizeLeftValue = Imgproc.getTextSize(String.format(Locale.getDefault(), "%.0f", leftValue), Core.FONT_HERSHEY_SIMPLEX, 0.3d, 1, null);
            Size textSizeRightValue = Imgproc.getTextSize(String.format(Locale.getDefault(), "%.0f", rightValue), Core.FONT_HERSHEY_SIMPLEX, 0.3d, 1, null);

            Imgproc.putText(valueMeasuredMat, String.format(Locale.getDefault(), "%.0f", leftValue),
                    new Point((xTranslate - xMargin) / 2 - textSizeLeftValue.width / 2, 15), Core.FONT_HERSHEY_SIMPLEX,
                    0.3d, labGrey, 1, Core.LINE_AA, false);
            Imgproc.putText(valueMeasuredMat, String.format(Locale.getDefault(), "%.0f", rightValue),
                    new Point(valueMeasuredMat.cols() - xMargin - (xTranslate - xMargin) / 2 - textSizeRightValue.width / 2, 15),
                    Core.FONT_HERSHEY_SIMPLEX, 0.3d, labGrey, 1, Core.LINE_AA, false);


            // we need to iterate over the result values to determine where the circle should be placed
            for (int d = 0; d < colours.length(); d++) {
                colourObj = colours.getJSONObject(d);
                if (d < colours.length() - 1) {
                    nextColourObj = colours.getJSONObject(d + 1);
                } else nextColourObj = colourObj;

                double value = colourObj.getDouble("value");
                double nextValue = nextColourObj.getDouble("value");

                if (result < nextValue && !resultIsDrawn) {

                    //calculate the amount above the lowest value
                    //calculate number of pixels needed to translate in x direction
                    double transX = xTranslate * ((result - value) / (nextValue - value));

                    //calculate where the center of the circle should be
                    double left = xTranslate * d;
                    double right = left + xTranslate - xMargin;
                    Point centerCircle = (transX) + xTranslate * d < xMargin ? new Point(xMargin, 25d) :
                            new Point(left + (right - left) / 2 + transX, 25d);

                    //get text size of value test
                    Size textSize = Imgproc.getTextSize(String.format(Locale.getDefault(), "%.1f", result), Core.FONT_HERSHEY_SIMPLEX, 0.35d, 1, null);
                    Imgproc.putText(valueMeasuredMat, String.format(Locale.getDefault(), "%.1f", result),
                            new Point(centerCircle.x - textSize.width / 2, 15d), Core.FONT_HERSHEY_SIMPLEX, 0.35d,
                            labBlack, 1, Core.LINE_AA, false);

                    double offset = circleRadius + 5;
                    for (ColorDetected aColorsDetected : colorsDetected) {
                        Scalar resultColor = aColorsDetected.getLab();
                        Imgproc.circle(valueMeasuredMat, new Point(centerCircle.x, centerCircle.y + offset), circleRadius, resultColor, -1, Imgproc.LINE_AA, 0);
                        offset += 2 * circleRadius + 5;
                    }

                    resultIsDrawn = true;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return valueMeasuredMat;
    }

    public static Mat concatenate(Mat m1, Mat m2) {
        int width = Math.max(m1.cols(), m2.cols());
        int height = m1.rows() + m2.rows();

        Mat result = new Mat(height, width, CvType.CV_8UC3, new Scalar(255, 255, 255));

        // rect works with x, y, width, height
        Rect roi1 = new Rect(0, 0, m1.cols(), m1.rows());
        Mat roiMat1 = result.submat(roi1);
        m1.copyTo(roiMat1);

        Rect roi2 = new Rect(0, m1.rows(), m2.cols(), m2.rows());
        Mat roiMat2 = result.submat(roi2);
        m2.copyTo(roiMat2);

        return result;
    }

    public static ColorDetected getPatchColour(Mat mat, Point centerPatch, int subMatSize) {

        //make a subMat around center of the patch
        int minRow = (int) Math.round(Math.max(centerPatch.y - subMatSize, 0));
        int maxRow = (int) Math.round(Math.min(centerPatch.y + subMatSize, mat.height()));
        int minCol = (int) Math.round(Math.max(centerPatch.x - subMatSize, 0));
        int maxCol = (int) Math.round(Math.min(centerPatch.x + subMatSize, mat.width()));

        //  create subMat
        Mat patch = mat.submat(minRow, maxRow, minCol, maxCol);

        // compute the mean colour and return it
        return OpenCVUtil.detectStripColorBrandKnown(patch);
    }

    /*
   * Restricts number of significant digits depending on size of number
    */
    public static double roundSignificant(double value) {
        if (value < 1.0) {
            return Math.round(value * 100) / 100.0;
        } else {
            return Math.round(value * 10) / 10.0;
        }
    }

    private static double[][] createInterpolTable(JSONArray colours) {
        JSONArray patchColorValues;
        double resultPatchValueStart, resultPatchValueEnd;
        double[] pointStart;
        double[] pointEnd;
        double LInter, aInter, bInter, vInter;
        double[][] interpolTable = new double[(colours.length() - 1) * INTERPOLATION_NUMBER + 1][4];
        int count = 0;

        for (int i = 0; i < colours.length() - 1; i++) {
            try {
                patchColorValues = colours.getJSONObject(i).getJSONArray("lab");
                resultPatchValueStart = colours.getJSONObject(i).getDouble("value");
                pointStart = new double[]{patchColorValues.getDouble(0), patchColorValues.getDouble(1), patchColorValues.getDouble(2)};

                patchColorValues = colours.getJSONObject(i + 1).getJSONArray("lab");
                resultPatchValueEnd = colours.getJSONObject(i + 1).getDouble("value");
                pointEnd = new double[]{patchColorValues.getDouble(0), patchColorValues.getDouble(1), patchColorValues.getDouble(2)};

                double LStart = pointStart[0];
                double aStart = pointStart[1];
                double bStart = pointStart[2];

                double dL = (pointEnd[0] - pointStart[0]) / INTERPOLATION_NUMBER;
                double da = (pointEnd[1] - pointStart[1]) / INTERPOLATION_NUMBER;
                double db = (pointEnd[2] - pointStart[2]) / INTERPOLATION_NUMBER;
                double dV = (resultPatchValueEnd - resultPatchValueStart) / INTERPOLATION_NUMBER;

                // create 10 interpolation points, including the start point,
                // but excluding the end point

                for (int ii = 0; ii < INTERPOLATION_NUMBER; ii++) {
                    LInter = LStart + ii * dL;
                    aInter = aStart + ii * da;
                    bInter = bStart + ii * db;
                    vInter = resultPatchValueStart + ii * dV;

                    interpolTable[count][0] = LInter;
                    interpolTable[count][1] = aInter;
                    interpolTable[count][2] = bInter;
                    interpolTable[count][3] = vInter;
                    count++;
                }

                // add final point
                patchColorValues = colours.getJSONObject(colours.length() - 1).getJSONArray("lab");
                interpolTable[count][0] = patchColorValues.getDouble(0);
                interpolTable[count][1] = patchColorValues.getDouble(1);
                interpolTable[count][2] = patchColorValues.getDouble(2);
                interpolTable[count][3] = colours.getJSONObject(colours.length() - 1).getDouble("value");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return interpolTable;
    }

    public static double calculateResultSingle(double[] colorValues, JSONArray colours) throws Exception {
        double[][] interpolTable = createInterpolTable(colours);

        // determine closest value
        // create interpolation and extrapolation tables using linear approximation
        if (colorValues == null || colorValues.length < 3) {
            throw new Exception("no valid lab colour data.");
        }

        // normalise lab values to standard ranges L:0..100, a and b: -127 ... 128
        double[] labPoint = new double[]{colorValues[0] / 2.55, colorValues[1] - 128, colorValues[2] - 128};

        double dist;
        int minPos = 0;
        double smallestE94Dist = Double.MAX_VALUE;

        for (int j = 0; j < interpolTable.length; j++) {
            // Find the closest point using the E94 distance
            // the values are already in the right range, so we don't need to normalize
            dist = CalibrationCard.E94(labPoint[0], labPoint[1], labPoint[2], interpolTable[j][0], interpolTable[j][1], interpolTable[j][2], false);
            if (dist < smallestE94Dist) {
                smallestE94Dist = dist;
                minPos = j;
            }
        }

        return interpolTable[minPos][3];
    }

    public static double calculateResultGroup(double[][] colorsValueLab, List<StripTest.Brand.Patch> patches) throws Exception {
        double[][][] interpolTables = new double[patches.size()][][];

        // create all interpol tables
        for (int p = 0; p < patches.size(); p++) {
            JSONArray colours = patches.get(p).getColours();

            // create interpol table for this patch
            interpolTables[p] = createInterpolTable(colours);

            if (colorsValueLab[p] == null || colorsValueLab[p].length < 3) {
                throw new Exception("no valid lab colour data.");
            }
        }

        // normalise lab values to standard ranges L:0..100, a and b: -127 ... 128
        double[][] labPoint = new double[patches.size()][];
        for (int p = 0; p < patches.size(); p++) {
            labPoint[p] = new double[]{colorsValueLab[p][0] / 2.55, colorsValueLab[p][1] - 128, colorsValueLab[p][2] - 128};
        }

        double dist;
        int minPos = 0;
        double smallestE94Dist = Double.MAX_VALUE;

        // compute smallest distance, combining all interpolation tables as we want the global minimum
        // all interpol tables should have the same length here, so we use the length of the first one

        for (int j = 0; j < interpolTables[0].length; j++) {
            dist = 0;
            for (int p = 0; p < patches.size(); p++) {
                dist += CalibrationCard.E94(labPoint[p][0], labPoint[p][1],
                        labPoint[p][2], interpolTables[p][j][0], interpolTables[p][j][1], interpolTables[p][j][2], false);
            }
            if (dist < smallestE94Dist) {
                smallestE94Dist = dist;
                minPos = j;
            }
        }

        return interpolTables[0][minPos][3];
    }

    private static String roundResult(double value) {
        if (value < 1.0) {
            return String.format(Locale.getDefault(), "%.2f", value);
        } else {
            return String.format(Locale.getDefault(), "%.1f", value);
        }
    }

    private static String roundAxis(double value) {
        if (value < 1.0) {
            return String.format(Locale.getDefault(), "%.1f", value);
        } else {
            return String.format(Locale.getDefault(), "%.0f", value);
        }
    }
}