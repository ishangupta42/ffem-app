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

package org.akvo.caddisfly.sensor.colorimetry.liquid;

import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TextView;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.model.Result;
import org.akvo.caddisfly.model.ResultDetail;
import org.akvo.caddisfly.sensor.SensorConstants;
import org.akvo.caddisfly.util.ColorUtil;

import java.util.ArrayList;
import java.util.Locale;

/**
 * A Dialog to display detailed result information of the analysis
 * <p/>
 * Only displayed in diagnostic mode
 */
public class DiagnosticResultDialog extends DialogFragment {

    private ArrayList<Result> mResults;
    private boolean mIsCalibration;

    public DiagnosticResultDialog() {
        // Required empty public constructor
    }

    /**
     * Returns a new instance of this dialog
     *
     * @param results       array list of results
     * @param allowRetry    whether to display retry button
     * @param result        the result value
     * @param color         the color value
     * @param isCalibration is this a calibration result
     * @return the dialog
     */
    public static DiagnosticResultDialog newInstance(ArrayList<Result> results, boolean allowRetry,
                                                     String result, int color, boolean isCalibration) {
        DiagnosticResultDialog fragment = new DiagnosticResultDialog();
        Bundle args = new Bundle();
        args.putBoolean("retry", allowRetry);
        args.putString(SensorConstants.RESULT, result);
        args.putInt("color", color);
        args.putBoolean("calibration", isCalibration);
        fragment.mResults = results;
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.dialog_diagnostic_result, container, false);

        ListView listResults = (ListView) view.findViewById(R.id.listResults);
        listResults.setAdapter(new ResultListAdapter());

        boolean allowRetry = getArguments().getBoolean("retry");
        int mColor = getArguments().getInt("color");

        mIsCalibration = getArguments().getBoolean("calibration");

        Button buttonCancel = (Button) view.findViewById(R.id.buttonCancel);
        Button buttonRetry = (Button) view.findViewById(R.id.buttonRetry);
        Button buttonOk = (Button) view.findViewById(R.id.buttonOk);

        //if allowRetry is true then this is an error show retry button
        if (allowRetry) {
            getDialog().setTitle(R.string.error);
            buttonCancel.setVisibility(View.VISIBLE);
            buttonRetry.setVisibility(View.VISIBLE);
            buttonOk.setVisibility(View.GONE);
            buttonCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    DiagnosticResultDialogListener listener = (DiagnosticResultDialogListener) getActivity();
                    listener.onFinishDiagnosticResultDialog(false, true, "", mIsCalibration);

                }
            });

            buttonRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    DiagnosticResultDialogListener listener = (DiagnosticResultDialogListener) getActivity();
                    listener.onFinishDiagnosticResultDialog(true, false, "", mIsCalibration);

                }
            });
        } else {
            final String result = getArguments().getString(SensorConstants.RESULT);
            if (mIsCalibration) {
                getDialog().setTitle(String.format("%s: %s", getString(R.string.result), ColorUtil.getColorRgbString(mColor)));
            } else {
                getDialog().setTitle(result);
            }

            buttonCancel.setVisibility(View.GONE);
            buttonRetry.setVisibility(View.GONE);
            buttonOk.setVisibility(View.VISIBLE);

            buttonOk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    DiagnosticResultDialogListener listener = (DiagnosticResultDialogListener) getActivity();
                    listener.onFinishDiagnosticResultDialog(false, false, result, mIsCalibration);

                }
            });
        }
        return view;
    }

    public interface DiagnosticResultDialogListener {
        void onFinishDiagnosticResultDialog(boolean retry, boolean cancelled, String result, boolean isCalibration);
    }

    private class ResultListAdapter extends BaseAdapter {

        public int getCount() {
            return mResults.size();
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View view, ViewGroup parent) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            @SuppressLint("ViewHolder")
            View rowView = inflater.inflate(R.layout.row_info, parent, false);

            if (rowView != null) {
                TextView textRgb = (TextView) rowView.findViewById(R.id.textRgb);
                ImageView imageView = (ImageView) rowView.findViewById(R.id.imageView);
                TextView textSwatch = (TextView) rowView.findViewById(R.id.textSwatch);

                Result result = mResults.get(position);

                imageView.setImageBitmap(result.getBitmap());
                int color = result.getResults().get(0).getColor();

                textSwatch.setBackgroundColor(color);

                if (mIsCalibration) {
                    TableLayout colorModelHeading = (TableLayout) rowView.findViewById(R.id.tableHeading);
                    colorModelHeading.setVisibility(View.GONE);
                }

                //display rgb value
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                textRgb.setText(String.format(Locale.getDefault(), "%d  %d  %d", r, g, b));

                ListView listResults = (ListView) rowView.findViewById(R.id.listResults);

                if (mIsCalibration) {
                    // Hide the results table and show only the color
                    listResults.setVisibility(View.GONE);
                } else {
                    // Show the results table
                    listResults.setAdapter(new ResultsDetailsAdapter(result.getResults()));
                }
            }
            return rowView;
        }
    }


    public class ResultsDetailsAdapter extends BaseAdapter {

        final ArrayList<ResultDetail> mResults;

        public ResultsDetailsAdapter(ArrayList<ResultDetail> results) {
            mResults = results;
        }

        public int getCount() {
            return 4;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View view, ViewGroup parent) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            @SuppressLint("ViewHolder")
            View rowView = inflater.inflate(R.layout.row_result, parent, false);

            int calibrationSteps = 2;
            switch (position) {
                case 0:
                    calibrationSteps = 1;
                    break;
                case 1:
                    calibrationSteps = 2;
                    break;
                case 2:
                    calibrationSteps = 3;
                    break;
                case 3:
                    calibrationSteps = 5;
                    break;
            }

            TextView textCalibrationSteps = (TextView) rowView.findViewById(R.id.textCalibrationSteps);
            textCalibrationSteps.setText(String.format("%s step", calibrationSteps));

            TextView textResult = null;

            for (ResultDetail resultDetail : mResults) {
                if (resultDetail.getCalibrationSteps() == calibrationSteps) {

                    switch (resultDetail.getColorModel()) {
                        case LAB:
                            textResult = (TextView) rowView.findViewById(R.id.textLabResult);
                            if (resultDetail.getResult() > -1) {
                                textResult.setText(String.format(Locale.getDefault(), "%.2f", resultDetail.getResult()));
                            } else {
                                textResult.setText(String.format(Locale.getDefault(), "(%.2f)", resultDetail.getDistance()));

                                textResult.setTextColor(ContextCompat.getColor(getContext(), R.color.diagnostic));
                                textResult.setTextSize(14);
                            }
                            break;
                        case RGB:
                            textResult = (TextView) rowView.findViewById(R.id.textRgbResult);
                            if (resultDetail.getResult() > -1) {
                                textResult.setText(String.format(Locale.getDefault(), "%.2f", resultDetail.getResult()));
                            } else {
                                textResult.setText(String.format(Locale.getDefault(), "(%.2f)", resultDetail.getDistance()));
                                textResult.setTextColor(ContextCompat.getColor(getContext(), R.color.diagnostic));
                                textResult.setTextSize(14);
                            }
                            break;
                    }

                    if (calibrationSteps == 5 && resultDetail.getColorModel() == ColorUtil.DEFAULT_COLOR_MODEL) {
                        textResult.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                }
            }
            return rowView;
        }
    }
}
