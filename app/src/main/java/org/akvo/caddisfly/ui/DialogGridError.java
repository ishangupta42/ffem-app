package org.akvo.caddisfly.ui;


import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.MainApp;
import org.akvo.caddisfly.util.ColorUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class DialogGridError extends DialogFragment {

    private final DecimalFormat doubleFormat = new DecimalFormat("0.00");
    ArrayList<Integer> mColors;
    ArrayList<Bitmap> mBitmaps;
    double[] mResults;
    boolean mAllowRetry;
    boolean mIsCalibration;

    public DialogGridError() {
        // Required empty public constructor
    }

    public static DialogGridError newInstance(ArrayList<Integer> colors, ArrayList<Double> results,
                                              ArrayList<Bitmap> bitmaps, boolean allowRetry,
                                              double result, int color, boolean isCalibration) {
        DialogGridError fragment = new DialogGridError();
        Bundle args = new Bundle();
        args.putBoolean("retry", allowRetry);
        args.putIntegerArrayList("colors", colors);
        args.putDoubleArray("results", convertDoubles(results));
        args.putDouble("result", result);
        args.putInt("color", color);
        args.putBoolean("calibration", isCalibration);
        fragment.mBitmaps = bitmaps;
        fragment.setArguments(args);
        return fragment;
    }

    public static double[] convertDoubles(List<Double> doubles) {
        double[] ret = new double[doubles.size()];
        for (int i = 0; i < ret.length; i++) ret[i] = doubles.get(i);
        return ret;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.dialog_grid_error, container, false);

        ListView resultList = (ListView) view.findViewById(R.id.resultList);
        resultList.setAdapter(new ImageAdapter());

        mColors = getArguments().getIntegerArrayList("colors");
        mResults = getArguments().getDoubleArray("results");
        mAllowRetry = getArguments().getBoolean("retry");
        int mColor = getArguments().getInt("color");

        mIsCalibration = getArguments().getBoolean("calibration");

        Button cancelButton = (Button) view.findViewById(R.id.cancelButton);
        Button retryButton = (Button) view.findViewById(R.id.retryButton);
        Button okButton = (Button) view.findViewById(R.id.okButton);

        if (mAllowRetry) {
            getDialog().setTitle(R.string.error);
            cancelButton.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            okButton.setVisibility(View.GONE);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    ErrorListDialogListener listener = (ErrorListDialogListener) getActivity();
                    listener.onFinishErrorListDialog(false, true);

                }
            });

            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    ErrorListDialogListener listener = (ErrorListDialogListener) getActivity();
                    listener.onFinishErrorListDialog(true, false);

                }
            });
        } else {
            double result = getArguments().getDouble("result");
            if (mIsCalibration) {
                getDialog().setTitle(String.format("%s: %s", getString(R.string.result), ColorUtils.getColorRgbString(mColor)));
            } else {
                getDialog().setTitle(String.format("%s: %.2f", getString(R.string.result), result));
            }

            cancelButton.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            okButton.setVisibility(View.VISIBLE);

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    ErrorListDialogListener listener = (ErrorListDialogListener) getActivity();
                    listener.onFinishErrorListDialog(false, false);

                }
            });
        }
        return view;
    }

    public interface ErrorListDialogListener {
        void onFinishErrorListDialog(boolean retry, boolean cancelled);
    }

    public class ImageAdapter extends BaseAdapter {
        //private Context mContext;

        //public ImageAdapter(Context c) {
        //mContext = c;
        //}

        public int getCount() {
            return mBitmaps.size();
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

            MainApp mainApp = ((MainApp) getActivity().getApplicationContext());

            if (mainApp != null && rowView != null) {
                TextView ppmText = (TextView) rowView.findViewById(R.id.ppmText);
                TextView rgbText = (TextView) rowView.findViewById(R.id.rgbText);
                ImageView imageView = (ImageView) rowView.findViewById(R.id.imageView);
                Button button = (Button) rowView.findViewById(R.id.button);

                imageView.setImageBitmap(mBitmaps.get(position));
                int color = mColors.get(position);

                button.setBackgroundColor(color);

                if (mIsCalibration) {
                    ppmText.setVisibility(View.INVISIBLE);
                } else if (mResults[position] > -1) {
                    ppmText.setText(doubleFormat.format(mResults[position]));
                }

                //display rgb value
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                rgbText.setText(String.format("%d  %d  %d", r, g, b));
            }
            return rowView;
        }
    }
}