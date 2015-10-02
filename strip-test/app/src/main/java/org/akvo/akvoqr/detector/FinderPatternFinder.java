/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.akvo.akvoqr.detector;


import org.opencv.core.Point;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.akvo.akvoqr.opencv.OpenCVUtils.getOrderedPoints;

/**
 * <p>This class attempts to find finder patterns in a QR Code. Finder patterns are the square
 * markers at three corners of a QR Code.</p>
 *
 * <p>This class is thread-safe but not reentrant. Each thread must allocate its own object.
 *
 * @author Sean Owen
 */
public class FinderPatternFinder {
  private static final int CODE_NOT_FOUND = -1;
  private static final int CENTER_QUORUM = 2;
  protected static final int MIN_SKIP = 3; // 1 pixel/module times 3 modules/center
  protected static final int MAX_MODULES = 84; // this is the height of our calibration card

  private final BitMatrix image;
  private final List<FinderPattern> possibleCenters;
  private boolean hasSkipped;
  private final int[] crossCheckStateCount;
  private final ResultPointCallback resultPointCallback;

  /**
   * <p>Creates a finder that will search the image for three finder patterns.</p>
   *
   * @param image image to search
   */
  public FinderPatternFinder(BitMatrix image) {
    this(image, null);
  }

  public FinderPatternFinder(BitMatrix image, ResultPointCallback resultPointCallback) {
    this.image = image;
    this.possibleCenters = new ArrayList<>();
    this.crossCheckStateCount = new int[5];
    this.resultPointCallback = resultPointCallback;
  }

  protected final BitMatrix getImage() {
    return image;
  }

  public final List<FinderPattern> getPossibleCenters() {
    return possibleCenters;
  }

  public final FinderPatternInfo find(Map<DecodeHintType,?> hints) throws NotFoundException {
    boolean tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER);
    boolean pureBarcode = hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE);
    int maxI = image.getHeight();
    int maxJ = image.getWidth();
    // We are looking for black/white/black/white/black modules in
    // 1:1:3:1:1 ratio; this tracks the number of such modules seen so far

    // Let's assume that the maximum version QR Code we support takes up 1/2 the height of the
    // image, and then account for the center being 3 modules in size. This gives the smallest
    // number of pixels the center could be, so skip this often. When trying harder, look for all
    // QR versions regardless of how dense they are.
    int iSkip = (6 * maxI) / (4 * MAX_MODULES);
    if (iSkip < MIN_SKIP || tryHarder) {
      iSkip = MIN_SKIP;
    }

    boolean done = false;
    int[] stateCount = new int[5];
    for (int i = iSkip - 1; i < maxI && !done; i += iSkip) {
      // Get a row of black/white values
      stateCount[0] = 0;
      stateCount[1] = 0;
      stateCount[2] = 0;
      stateCount[3] = 0;
      stateCount[4] = 0;
      int currentState = 0;
      for (int j = 0; j < maxJ; j++) {
        if (image.get(j, i)) {
          // Black pixel
          if ((currentState & 1) == 1) { // Counting white pixels
            currentState++;
          }
          stateCount[currentState]++;
        } else { // White pixel
          if ((currentState & 1) == 0) { // Counting black pixels
            if (currentState == 4) { // A winner?
              if (foundPatternCross(stateCount)) { // Yes
                boolean confirmed = handlePossibleCenter(stateCount, i, j, pureBarcode);
                if (confirmed) {
                  // Start examining every other line. Checking each line turned out to be too
                  // expensive and didn't improve performance.
                  iSkip = 2;
                  if (hasSkipped) {
                    done = haveMultiplyConfirmedCenters();
                  } else {
                    int rowSkip = findRowSkip();
                    if (rowSkip > stateCount[2]) {
                      // Skip rows between row of lower confirmed center
                      // and top of presumed third confirmed center
                      // but back up a bit to get a full chance of detecting
                      // it, entire width of center of finder pattern

                      // Skip by rowSkip, but back off by stateCount[2] (size of last center
                      // of pattern we saw) to be conservative, and also back off by iSkip which
                      // is about to be re-added
                      i += rowSkip - stateCount[2] - iSkip;
                      j = maxJ - 1;
                    }
                  }
                } else {
                  stateCount[0] = stateCount[2];
                  stateCount[1] = stateCount[3];
                  stateCount[2] = stateCount[4];
                  stateCount[3] = 1;
                  stateCount[4] = 0;
                  currentState = 3;
                  continue;
                }
                // Clear state to start looking again
                currentState = 0;
                stateCount[0] = 0;
                stateCount[1] = 0;
                stateCount[2] = 0;
                stateCount[3] = 0;
                stateCount[4] = 0;
              } else { // No, shift counts back by two
                stateCount[0] = stateCount[2];
                stateCount[1] = stateCount[3];
                stateCount[2] = stateCount[4];
                stateCount[3] = 1;
                stateCount[4] = 0;
                currentState = 3;
              }
            } else {
              stateCount[++currentState]++;
            }
          } else { // Counting white pixels
            stateCount[currentState]++;
          }
        }
      }
      if (foundPatternCross(stateCount)) {
        boolean confirmed = handlePossibleCenter(stateCount, i, maxJ, pureBarcode);
        if (confirmed) {
          iSkip = stateCount[0];
          if (hasSkipped) {
            // Found a third one
            done = haveMultiplyConfirmedCenters();
          }
        }
      }
    }

    FinderPattern[] patternInfo = selectBestPatterns();

    int code = decodeCallibrationCardCode(patternInfo);

    return new FinderPatternInfo(patternInfo,code);
  }

  /**
   * find and decode the code of the callibration card
   * The code is stored as a simple barcode. It starts 4.5 modules from the center of the bottom left finder pattern
   * and extends to module 29.5.
   * It has 12 bits, of 2 modules wide each.
   * It starts and ends with a 1 bit.
   * The remaining 10 bits are interpreted as a 9 bit number with the last bit as parity bit.
   *
   * @param patternInfo
   */
  private int decodeCallibrationCardCode(FinderPattern[] patternInfo) {
    // order points
    if (patternInfo.length == 4) {
      double[] p1 = new double[]{patternInfo[0].getX(), patternInfo[0].getY()};
      double[] p2 = new double[]{patternInfo[1].getX(), patternInfo[1].getY()};
      double[] p3 = new double[]{patternInfo[2].getX(), patternInfo[2].getY()};
      double[] p4 = new double[]{patternInfo[3].getX(), patternInfo[3].getY()};

      // sort points in order top-left, bottom-left, top-right, bottom-right
      List<Point> points = getOrderedPoints(p1,p2,p3,p4);

      ResultPoint bottomLeft = new ResultPoint((float) points.get(1).x,(float) points.get(1).y);
      ResultPoint bottomRight = new ResultPoint((float) points.get(3).x,(float) points.get(3).y);

      // get estimated module size
      Detector detector = new Detector(image);
      float modSizeHor = detector.calculateModuleSize(bottomLeft, bottomRight, bottomRight);

      // go from one finder pattern to the other,
      double lrx = bottomRight.getX() - bottomLeft.getX();
      double lry = bottomRight.getY() - bottomLeft.getY();
      double hNorm = MathUtils.distance(bottomLeft.getX(), bottomLeft.getY(), bottomRight.getX(), bottomRight.getY());

      // check if left and right are ok
      if (lrx < 0) {
        return CODE_NOT_FOUND;
      }

      // create vector of length 1 pixel, in the direction of the bottomRight finder pattern
      lrx /= hNorm;
      lry /= hNorm;

      // sample line into new row
      boolean[] bits = new boolean[image.getWidth()];
      int index = 0;
      double px = bottomLeft.getX();
      double py = bottomLeft.getY();
      while (px < bottomRight.getX() && px > 0 && py > 0 && px < image.getWidth() && py < image.getHeight()){
        bits[index] = image.get((int) Math.round(px),(int) Math.round(py));
        px += lrx;
        py += lry;
        index++;
      }

      // starting index: 4.5 modules in the direction of the bottom right finder pattern
      // end index: our pattern ends at module 17, so we take 25 to be sure.
      int startIndex = (int) Math.round(4.5 * modSizeHor / lrx);
      int endIndex = (int) Math.round(25 * modSizeHor / lrx);

      // determine start of pattern: first black bit. Approach from the left
      int startI = startIndex;
      while (startI < endIndex && !bits[startI]){
        startI++;
      }

      // determine end of pattern: last black bit. Approach from the right
      int endI = endIndex;
      while (endI > startI && !bits[endI]){
        endI--;
      }

      int lengthPattern = endI - startI + 1;

      // sanity check on length of pattern.
      // We put the minimum size at 20 pixels, which would correspond to a module size of less than 2 pixels, which is too small.
      if (lengthPattern < 20) {
        return CODE_NOT_FOUND;
      }
      
      double pWidth = lengthPattern / 12.0;

      // determine bits by majority voting
      int[] bitVote = new int[12];
      for (int i = 0; i < 12; i++){
        bitVote[i] = 0;
      }

      int bucket;
      for (int i = startI; i <= endI; i++){
        bucket = (int) Math.round(Math.floor((i - startI) / pWidth));
        bitVote[bucket] += bits[i] ? 1 : -1;
      }

      // translate into information bits. Skip first and last, which are always 1
      boolean[] bitResult = new boolean[10]; // will contain the information bits
      for (int i = 1; i < 11; i++){
        bitResult[i - 1] = bitVote[i] > 0;
      }

      // check parity bit
      if (parity(bitResult) != bitResult[9]) {
        return CODE_NOT_FOUND;
      }

      // compute result
      int code = 0;
      int count = 0;
      for (int i = 8; i >= 0; i--){
        if (bitResult[i]){
          code += (int) Math.pow(2,count);
        }
        count ++;
      }

      return code;
    }
    else {
      return CODE_NOT_FOUND;
    }
  }

  /**
   * Compute even parity, where last bit is the even parity bit
   */
  private static boolean parity(boolean[] bits){
    int oneCount = 0;
    for (int i = 0; i < bits.length - 1; i++) {  // skip parity bit in calculation of parity
      if (bits[i]) {
        oneCount++;
      }
    }
    return oneCount % 2 != 0; // returns true if parity is odd
  }

  /**
   * Given a count of black/white/black/white/black pixels just seen and an end position,
   * figures the location of the center of this run.
   */
  private static float centerFromEnd(int[] stateCount, int end) {
    return (float) (end - stateCount[4] - stateCount[3]) - stateCount[2] / 2.0f;
  }

  /**
   * @param stateCount count of black/white/black/white/black pixels just read
   * @return true iff the proportions of the counts is close enough to the 1/1/3/1/1 ratios
   *         used by finder patterns to be considered a match
   */
  protected static boolean foundPatternCross(int[] stateCount) {
    int totalModuleSize = 0;
    for (int i = 0; i < 5; i++) {
      int count = stateCount[i];
      if (count == 0) {
        return false;
      }
      totalModuleSize += count;
    }
    if (totalModuleSize < 7) {
      return false;
    }
    float moduleSize = totalModuleSize / 7.0f;
    float maxVariance = moduleSize / 2.0f;
    // Allow less than 50% variance from 1-1-3-1-1 proportions
    return
            Math.abs(moduleSize - stateCount[0]) < maxVariance &&
                    Math.abs(moduleSize - stateCount[1]) < maxVariance &&
                    Math.abs(3.0f * moduleSize - stateCount[2]) < 3 * maxVariance &&
                    Math.abs(moduleSize - stateCount[3]) < maxVariance &&
                    Math.abs(moduleSize - stateCount[4]) < maxVariance;
  }

  private int[] getCrossCheckStateCount() {
    crossCheckStateCount[0] = 0;
    crossCheckStateCount[1] = 0;
    crossCheckStateCount[2] = 0;
    crossCheckStateCount[3] = 0;
    crossCheckStateCount[4] = 0;
    return crossCheckStateCount;
  }

  /**
   * After a vertical and horizontal scan finds a potential finder pattern, this method
   * "cross-cross-cross-checks" by scanning down diagonally through the center of the possible
   * finder pattern to see if the same proportion is detected.
   *
   * @param startI row where a finder pattern was detected
   * @param centerJ center of the section that appears to cross a finder pattern
   * @param maxCount maximum reasonable number of modules that should be
   *  observed in any reading state, based on the results of the horizontal scan
   * @param originalStateCountTotal The original state count total.
   * @return true if proportions are withing expected limits
   */
  private boolean crossCheckDiagonal(int startI, int centerJ, int maxCount, int originalStateCountTotal) {
    int[] stateCount = getCrossCheckStateCount();

    // Start counting up, left from center finding black center mass
    int i = 0;
    while (startI >= i && centerJ >= i && image.get(centerJ - i, startI - i)) {
      stateCount[2]++;
      i++;
    }

    if (startI < i || centerJ < i) {
      return false;
    }

    // Continue up, left finding white space
    while (startI >= i && centerJ >= i && !image.get(centerJ - i, startI - i) &&
            stateCount[1] <= maxCount) {
      stateCount[1]++;
      i++;
    }

    // If already too many modules in this state or ran off the edge:
    if (startI < i || centerJ < i || stateCount[1] > maxCount) {
      return false;
    }

    // Continue up, left finding black border
    while (startI >= i && centerJ >= i && image.get(centerJ - i, startI - i) &&
            stateCount[0] <= maxCount) {
      stateCount[0]++;
      i++;
    }
    if (stateCount[0] > maxCount) {
      return false;
    }

    int maxI = image.getHeight();
    int maxJ = image.getWidth();

    // Now also count down, right from center
    i = 1;
    while (startI + i < maxI && centerJ + i < maxJ && image.get(centerJ + i, startI + i)) {
      stateCount[2]++;
      i++;
    }

    // Ran off the edge?
    if (startI + i >= maxI || centerJ + i >= maxJ) {
      return false;
    }

    while (startI + i < maxI && centerJ + i < maxJ && !image.get(centerJ + i, startI + i) &&
            stateCount[3] < maxCount) {
      stateCount[3]++;
      i++;
    }

    if (startI + i >= maxI || centerJ + i >= maxJ || stateCount[3] >= maxCount) {
      return false;
    }

    while (startI + i < maxI && centerJ + i < maxJ && image.get(centerJ + i, startI + i) &&
            stateCount[4] < maxCount) {
      stateCount[4]++;
      i++;
    }

    if (stateCount[4] >= maxCount) {
      return false;
    }

    // If we found a finder-pattern-like section, but its size is more than 100% different than
    // the original, assume it's a false positive
    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4];
    return
            Math.abs(stateCountTotal - originalStateCountTotal) < 2 * originalStateCountTotal &&
                    foundPatternCross(stateCount);
  }

  /**
   * <p>After a horizontal scan finds a potential finder pattern, this method
   * "cross-checks" by scanning down vertically through the center of the possible
   * finder pattern to see if the same proportion is detected.</p>
   *
   * @param startI row where a finder pattern was detected
   * @param centerJ center of the section that appears to cross a finder pattern
   * @param maxCount maximum reasonable number of modules that should be
   * observed in any reading state, based on the results of the horizontal scan
   * @return vertical center of finder pattern, or {@link Float#NaN} if not found
   */
  private float crossCheckVertical(int startI, int centerJ, int maxCount,
                                   int originalStateCountTotal) {
    BitMatrix image = this.image;

    int maxI = image.getHeight();
    int[] stateCount = getCrossCheckStateCount();

    // Start counting up from center
    int i = startI;
    while (i >= 0 && image.get(centerJ, i)) {
      stateCount[2]++;
      i--;
    }
    if (i < 0) {
      return Float.NaN;
    }
    while (i >= 0 && !image.get(centerJ, i) && stateCount[1] <= maxCount) {
      stateCount[1]++;
      i--;
    }
    // If already too many modules in this state or ran off the edge:
    if (i < 0 || stateCount[1] > maxCount) {
      return Float.NaN;
    }
    while (i >= 0 && image.get(centerJ, i) && stateCount[0] <= maxCount) {
      stateCount[0]++;
      i--;
    }
    if (stateCount[0] > maxCount) {
      return Float.NaN;
    }

    // Now also count down from center
    i = startI + 1;
    while (i < maxI && image.get(centerJ, i)) {
      stateCount[2]++;
      i++;
    }
    if (i == maxI) {
      return Float.NaN;
    }
    while (i < maxI && !image.get(centerJ, i) && stateCount[3] < maxCount) {
      stateCount[3]++;
      i++;
    }
    if (i == maxI || stateCount[3] >= maxCount) {
      return Float.NaN;
    }
    while (i < maxI && image.get(centerJ, i) && stateCount[4] < maxCount) {
      stateCount[4]++;
      i++;
    }
    if (stateCount[4] >= maxCount) {
      return Float.NaN;
    }

    // If we found a finder-pattern-like section, but its size is more than 40% different than
    // the original, assume it's a false positive
    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
            stateCount[4];
    if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {
      return Float.NaN;
    }

    return foundPatternCross(stateCount) ? centerFromEnd(stateCount, i) : Float.NaN;
  }

  /**
   * <p>Like {@link #crossCheckVertical(int, int, int, int)}, and in fact is basically identical,
   * except it reads horizontally instead of vertically. This is used to cross-cross
   * check a vertical cross check and locate the real center of the alignment pattern.</p>
   */
  private float crossCheckHorizontal(int startJ, int centerI, int maxCount,
                                     int originalStateCountTotal) {
    BitMatrix image = this.image;

    int maxJ = image.getWidth();
    int[] stateCount = getCrossCheckStateCount();

    int j = startJ;
    while (j >= 0 && image.get(j, centerI)) {
      stateCount[2]++;
      j--;
    }
    if (j < 0) {
      return Float.NaN;
    }
    while (j >= 0 && !image.get(j, centerI) && stateCount[1] <= maxCount) {
      stateCount[1]++;
      j--;
    }
    if (j < 0 || stateCount[1] > maxCount) {
      return Float.NaN;
    }
    while (j >= 0 && image.get(j, centerI) && stateCount[0] <= maxCount) {
      stateCount[0]++;
      j--;
    }
    if (stateCount[0] > maxCount) {
      return Float.NaN;
    }

    j = startJ + 1;
    while (j < maxJ && image.get(j, centerI)) {
      stateCount[2]++;
      j++;
    }
    if (j == maxJ) {
      return Float.NaN;
    }
    while (j < maxJ && !image.get(j, centerI) && stateCount[3] < maxCount) {
      stateCount[3]++;
      j++;
    }
    if (j == maxJ || stateCount[3] >= maxCount) {
      return Float.NaN;
    }
    while (j < maxJ && image.get(j, centerI) && stateCount[4] < maxCount) {
      stateCount[4]++;
      j++;
    }
    if (stateCount[4] >= maxCount) {
      return Float.NaN;
    }

    // If we found a finder-pattern-like section, but its size is significantly different than
    // the original, assume it's a false positive
    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
            stateCount[4];
    if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= originalStateCountTotal) {
      return Float.NaN;
    }

    return foundPatternCross(stateCount) ? centerFromEnd(stateCount, j) : Float.NaN;
  }

  /**
   * <p>This is called when a horizontal scan finds a possible alignment pattern. It will
   * cross check with a vertical scan, and if successful, will, ah, cross-cross-check
   * with another horizontal scan. This is needed primarily to locate the real horizontal
   * center of the pattern in cases of extreme skew.
   * And then we cross-cross-cross check with another diagonal scan.</p>
   *
   * <p>If that succeeds the finder pattern location is added to a list that tracks
   * the number of times each location has been nearly-matched as a finder pattern.
   * Each additional find is more evidence that the location is in fact a finder
   * pattern center
   *
   * @param stateCount reading state module counts from horizontal scan
   * @param i row where finder pattern may be found
   * @param j end of possible finder pattern in row
   * @param pureBarcode true if in "pure barcode" mode
   * @return true if a finder pattern candidate was found this time
   */
  protected final boolean handlePossibleCenter(int[] stateCount, int i, int j, boolean pureBarcode) {
    int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
            stateCount[4];
    float centerJ = centerFromEnd(stateCount, j);
    float centerI = crossCheckVertical(i, (int) centerJ, stateCount[2], stateCountTotal);
    if (!Float.isNaN(centerI)) {
      // Re-cross check
      centerJ = crossCheckHorizontal((int) centerJ, (int) centerI, stateCount[2], stateCountTotal);
      if (!Float.isNaN(centerJ) &&
              (!pureBarcode || crossCheckDiagonal((int) centerI, (int) centerJ, stateCount[2], stateCountTotal))) {
        float estimatedModuleSize = (float) stateCountTotal / 7.0f;
        boolean found = false;
        for (int index = 0; index < possibleCenters.size(); index++) {
          FinderPattern center = possibleCenters.get(index);
          // Look for about the same center and module size:
          if (center.aboutEquals(estimatedModuleSize, centerI, centerJ)) {
            possibleCenters.set(index, center.combineEstimate(centerI, centerJ, estimatedModuleSize));
            found = true;
            break;
          }
        }
        if (!found) {
          FinderPattern point = new FinderPattern(centerJ, centerI, estimatedModuleSize);
          possibleCenters.add(point);
          if (resultPointCallback != null) {
            resultPointCallback.foundPossibleResultPoint(point);
          }
        }
        return true;
      }
    }
    return false;
  }

  /**
   * @return number of rows we could safely skip during scanning, based on the first
   *         two finder patterns that have been located. In some cases their position will
   *         allow us to infer that the third pattern must lie below a certain point farther
   *         down in the image.
   */
  private int findRowSkip() {
    int max = possibleCenters.size();
    if (max <= 1) {
      return 0;
    }
    ResultPoint firstConfirmedCenter = null;
    for (FinderPattern center : possibleCenters) {
      if (center.getCount() >= CENTER_QUORUM) {
        if (firstConfirmedCenter == null) {
          firstConfirmedCenter = center;
        } else {
          // We have two confirmed centers
          // How far down can we skip before resuming looking for the next
          // pattern? In the worst case, only the difference between the
          // difference in the x / y coordinates of the two centers.
          // This is the case where you find top left last.
          hasSkipped = true;
          return (int) (Math.abs(firstConfirmedCenter.getX() - center.getX()) -
                  Math.abs(firstConfirmedCenter.getY() - center.getY())) / 3;
        }
      }
    }
    return 0;
  }

  /**
   * @return true iff we have found at least 4 finder patterns that have been detected
   *         at least {@link #CENTER_QUORUM} times each, and, the estimated module size of the
   *         candidates is "pretty similar"
   */
  private boolean haveMultiplyConfirmedCenters() {
    int confirmedCount = 0;
    float totalModuleSize = 0.0f;
    int max = possibleCenters.size();
    for (FinderPattern pattern : possibleCenters) {
      if (pattern.getCount() >= CENTER_QUORUM) {
        confirmedCount++;
        totalModuleSize += pattern.getEstimatedModuleSize();
      }
    }
    if (confirmedCount < 4) {
      return false;
    }
    // OK, we have at least 4 confirmed centers, but, it's possible that one is a "false positive"
    // and that we need to keep looking. We detect this by asking if the estimated module sizes
    // vary too much. We arbitrarily say that when the total deviation from average exceeds
    // 5% of the total module size estimates, it's too much.
    float average = totalModuleSize / (float) max;
    float totalDeviation = 0.0f;
    for (FinderPattern pattern : possibleCenters) {
      totalDeviation += Math.abs(pattern.getEstimatedModuleSize() - average);
    }
    return totalDeviation <= 0.05f * totalModuleSize;
  }

  /**
   * @return the 4 best {@link FinderPattern}s from our list of candidates. The "best" are
   *         those that have been detected at least {@link #CENTER_QUORUM} times, and whose module
   *         size differs from the average among those patterns the least
   * @throws NotFoundException if 4 such finder patterns do not exist
   */
  private FinderPattern[] selectBestPatterns() throws NotFoundException {

    int startSize = possibleCenters.size();
    if (startSize < 4) {
      // Couldn't find enough finder patterns
      throw NotFoundException.getNotFoundInstance();
    }

    // Filter outlier possibilities whose module size is too different
    if (startSize > 4) {
      // But we can only afford to do so if we have at least 5 possibilities to choose from
      float totalModuleSize = 0.0f;
      float square = 0.0f;
      for (FinderPattern center : possibleCenters) {
        float size = center.getEstimatedModuleSize();
        totalModuleSize += size;
        square += size * size;
      }
      float average = totalModuleSize / (float) startSize;
      float stdDev = (float) Math.sqrt(square / startSize - average * average);

      Collections.sort(possibleCenters, new FurthestFromAverageComparator(average));

      float limit = Math.max(0.2f * average, stdDev);

      for (int i = 0; i < possibleCenters.size() && possibleCenters.size() > 4; i++) {
        FinderPattern pattern = possibleCenters.get(i);
        if (Math.abs(pattern.getEstimatedModuleSize() - average) > limit) {
          possibleCenters.remove(i);
          i--;
        }
      }
    }

    if (possibleCenters.size() > 4) {
      // Throw away all but those first size candidate points we found.

      float totalModuleSize = 0.0f;
      for (FinderPattern possibleCenter : possibleCenters) {
        totalModuleSize += possibleCenter.getEstimatedModuleSize();
      }

      float average = totalModuleSize / (float) possibleCenters.size();

      Collections.sort(possibleCenters, new CenterComparator(average));

      possibleCenters.subList(4, possibleCenters.size()).clear();
    }

    return new FinderPattern[]{
            possibleCenters.get(0),
            possibleCenters.get(1),
            possibleCenters.get(2),
            possibleCenters.get(3)
    };
  }

  /**
   * <p>Orders by furthest from average</p>
   */
  private static final class FurthestFromAverageComparator implements Comparator<FinderPattern>, Serializable {
    private final float average;
    private FurthestFromAverageComparator(float f) {
      average = f;
    }
    @Override
    public int compare(FinderPattern center1, FinderPattern center2) {
      float dA = Math.abs(center2.getEstimatedModuleSize() - average);
      float dB = Math.abs(center1.getEstimatedModuleSize() - average);
      return dA < dB ? -1 : dA == dB ? 0 : 1;
    }
  }

  /**
   * <p>Orders by {@link FinderPattern#getCount()}, descending.</p>
   */
  private static final class CenterComparator implements Comparator<FinderPattern>, Serializable {
    private final float average;
    private CenterComparator(float f) {
      average = f;
    }
    @Override
    public int compare(FinderPattern center1, FinderPattern center2) {
      if (center2.getCount() == center1.getCount()) {
        float dA = Math.abs(center2.getEstimatedModuleSize() - average);
        float dB = Math.abs(center1.getEstimatedModuleSize() - average);
        return dA < dB ? 1 : dA == dB ? 0 : -1;
      } else {
        return center2.getCount() - center1.getCount();
      }
    }
  }

}
