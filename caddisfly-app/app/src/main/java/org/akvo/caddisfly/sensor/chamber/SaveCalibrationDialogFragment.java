/*
 * Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Caddisfly.
 *
 * Akvo Caddisfly is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akvo Caddisfly is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akvo Caddisfly. If not, see <http://www.gnu.org/licenses/>.
 */

package org.akvo.caddisfly.sensor.chamber;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.app.CaddisflyApp;
import org.akvo.caddisfly.dao.CalibrationDao;
import org.akvo.caddisfly.entity.CalibrationDetail;
import org.akvo.caddisfly.helper.FileHelper;
import org.akvo.caddisfly.helper.SwatchHelper;
import org.akvo.caddisfly.model.TestInfo;
import org.akvo.caddisfly.preference.AppPreferences;
import org.akvo.caddisfly.util.AlertUtil;
import org.akvo.caddisfly.util.FileUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import timber.log.Timber;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SaveCalibrationDialogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SaveCalibrationDialogFragment extends DialogFragment {

    private static final String ARG_TEST_INFO = "testInfo";
    private final Calendar calendar = Calendar.getInstance();
    private TestInfo mTestInfo;
    private EditText editName = null;
    private EditText editExpiryDate;
    private boolean isEditing = false;
    private Spinner spinnerCuvette;
    private TextView textError;

    private OnCalibrationDetailsSavedListener mListener;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment SaveCalibrationDialogFragment.
     */
    public static SaveCalibrationDialogFragment newInstance(TestInfo testInfo, boolean isEdit) {
        SaveCalibrationDialogFragment fragment = new SaveCalibrationDialogFragment();
        fragment.isEditing = isEdit;
        Bundle args = new Bundle();
        args.putParcelable(ARG_TEST_INFO, testInfo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mTestInfo = getArguments().getParcelable(ARG_TEST_INFO);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final Activity activity = getActivity();
        LayoutInflater i = activity.getLayoutInflater();

        @SuppressLint("InflateParams")
        View view = i.inflate(R.layout.fragment_save_calibration, null);

        editExpiryDate = view.findViewById(R.id.editExpiryDate);

        CalibrationDetail calibrationDetail = CaddisflyApp.getApp().getDb()
                .calibrationDao().getCalibrationDetails(mTestInfo.getUuid());

        if (calibrationDetail.expiry > new Date().getTime()) {

            if (calibrationDetail.expiry >= 0) {
                calendar.setTimeInMillis(calibrationDetail.expiry);

                editExpiryDate.setText(new SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                        .format(new Date(calibrationDetail.expiry)));
            }
        }

        setupDatePicker(activity);

        spinnerCuvette = view.findViewById(R.id.spinner);
        if (AppPreferences.isDiagnosticMode()) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity,
                    R.array.cuvettes, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCuvette.setAdapter(adapter);

            textError = view.findViewById(R.id.textError);

            for (int j = 0; j < adapter.getCount(); j++) {
                if (Objects.equals(adapter.getItem(j), calibrationDetail.cuvetteType)) {
                    spinnerCuvette.setSelection(j);
                }
            }

            spinnerCuvette.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    textError.setError(null);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    textError.setError(null);
                }
            });
        } else {
            spinnerCuvette.setVisibility(View.GONE);
            view.findViewById(R.id.layoutSpinner).setVisibility(View.GONE);
        }

        editName = view.findViewById(R.id.editName);
        if (!isEditing && AppPreferences.isDiagnosticMode()) {
            editName.requestFocus();
            showKeyboard(activity);

        } else {
            editName.setVisibility(View.GONE);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.calibrationDetails)
                .setPositiveButton(R.string.save,
                        (dialog, whichButton) -> {
                            closeKeyboard(activity, editName);
                            dismiss();
                        }
                )
                .setNegativeButton(R.string.cancel,
                        (dialog, whichButton) -> {
                            closeKeyboard(activity, editName);
                            dismiss();
                        }
                );

        b.setView(view);
        return b.create();
    }

    private void showKeyboard(Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void setupDatePicker(Context context) {

        final DatePickerDialog.OnDateSetListener onDateSetListener = (view13, year, monthOfYear, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            String date = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(calendar.getTime());
            editExpiryDate.setText(date);
        };

        final DatePickerDialog datePickerDialog = new DatePickerDialog(context, onDateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        Calendar date = Calendar.getInstance();
        date.add(Calendar.DATE, 1);
        date.set(Calendar.HOUR_OF_DAY, date.getMinimum(Calendar.HOUR_OF_DAY));
        date.set(Calendar.MINUTE, date.getMinimum(Calendar.MINUTE));
        date.set(Calendar.SECOND, date.getMinimum(Calendar.SECOND));
        date.set(Calendar.MILLISECOND, date.getMinimum(Calendar.MILLISECOND));
        datePickerDialog.getDatePicker().setMinDate(date.getTimeInMillis());
        if (mTestInfo.getMonthsValid() != null) {
            date.add(Calendar.MONTH, mTestInfo.getMonthsValid());
            date.set(Calendar.HOUR_OF_DAY, date.getMaximum(Calendar.HOUR_OF_DAY));
            date.set(Calendar.MINUTE, date.getMaximum(Calendar.MINUTE));
            date.set(Calendar.SECOND, date.getMaximum(Calendar.SECOND));
            date.set(Calendar.MILLISECOND, date.getMaximum(Calendar.MILLISECOND));
            datePickerDialog.getDatePicker().setMaxDate(date.getTimeInMillis());
        }

        editExpiryDate.setOnFocusChangeListener((view1, b) -> {
            if (b) {
                closeKeyboard(getContext(), editName);
                datePickerDialog.show();
            }
        });

        editExpiryDate.setOnClickListener(view12 -> {
            closeKeyboard(getContext(), editName);
            datePickerDialog.show();
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        final Context context = getActivity();

        final AlertDialog d = (AlertDialog) getDialog();
        if (d != null) {
            Button positiveButton = d.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (formEntryValid()) {

                        final String testCode = mTestInfo.getUuid();

                        if (!editName.getText().toString().trim().isEmpty()) {

                            final File path = FileHelper.getFilesDir(FileHelper.FileType.CALIBRATION, testCode);

                            File file = new File(path, editName.getText().toString());

                            if (file.exists()) {
                                AlertUtil.askQuestion(context, R.string.fileAlreadyExists,
                                        R.string.doYouWantToOverwrite, R.string.overwrite, R.string.cancel, true,
                                        (dialogInterface, i) -> {
                                            saveDetails(testCode);
                                            saveCalibrationDetails(path);
                                            closeKeyboard(context, editName);
                                            dismiss();
                                        }, null
                                );
                            } else {
                                saveDetails(testCode);
                                saveCalibrationDetails(path);
                                closeKeyboard(context, editName);
                                dismiss();
                            }
                        } else {
                            saveDetails(testCode);
                            closeKeyboard(context, editExpiryDate);
                            dismiss();
                        }
                    }
                }

                void saveDetails(String testCode) {

                    CalibrationDetail calibrationDetail = new CalibrationDetail();
                    calibrationDetail.uid = testCode;
                    calibrationDetail.date = Calendar.getInstance().getTimeInMillis();
                    calibrationDetail.expiry = calendar.getTimeInMillis();

                    if (AppPreferences.isDiagnosticMode()) {
                        calibrationDetail.cuvetteType = spinnerCuvette.getSelectedItem().toString();
                    }

                    CalibrationDao dao = CaddisflyApp.getApp().getDb().calibrationDao();
                    dao.insert(calibrationDetail);

                    mListener.onCalibrationDetailsSaved();
                }

                private boolean formEntryValid() {

                    if (!isEditing && AppPreferences.isDiagnosticMode()
                            && editName.getText().toString().trim().isEmpty()) {
                        editName.setError(getString(R.string.saveInvalidFileName));
                        return false;
                    }

                    if (AppPreferences.isDiagnosticMode()) {

                        textError.setError(null);

                        if (spinnerCuvette.getSelectedItemPosition() == 0) {
                            textError.requestFocus();
                            textError.setError("Select cuvette type");
                            return false;
                        }
                    }

                    if (editExpiryDate.getText().toString().trim().isEmpty()) {
                        editExpiryDate.setError(getString(R.string.required));
                        return false;
                    }

                    return true;
                }
            });
        }
    }

    private void saveCalibrationDetails(File path) {
        final Context context = getContext();

        final String calibrationDetails = SwatchHelper.generateCalibrationFile(context, mTestInfo, true);

        FileUtil.saveToFile(path, editName.getText().toString().trim(), calibrationDetails);

        Toast.makeText(context, R.string.fileSaved, Toast.LENGTH_SHORT).show();
    }

    /**
     * Hides the keyboard.
     *
     * @param input the EditText for which the keyboard is open
     */
    private void closeKeyboard(Context context, EditText input) {
        try {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                if (getActivity() != null) {
                    View view = getActivity().getCurrentFocus();
                    if (view != null) {
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        closeKeyboard(getActivity(), editName);
    }

    @Override
    public void onPause() {
        super.onPause();
        closeKeyboard(getActivity(), editName);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnCalibrationDetailsSavedListener) {
            mListener = (OnCalibrationDetailsSavedListener) context;
        } else {
            throw new IllegalArgumentException(context.toString()
                    + " must implement OnCalibrationDetailsSavedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnCalibrationDetailsSavedListener {
        void onCalibrationDetailsSaved();
    }
}
