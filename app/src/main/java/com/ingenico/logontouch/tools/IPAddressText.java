package com.ingenico.logontouch.tools;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;

/**
 * Created by vigursky on 22.11.2017.
 */

public class IPAddressText extends AppCompatEditText {

    public IPAddressText(Context context) {
        super(context);
        initIPAddressFilter();
    }

    public IPAddressText(Context context, AttributeSet attrs) {
        super(context, attrs, android.support.v7.appcompat.R.attr.editTextStyle);
        initIPAddressFilter();
    }

    private void initIPAddressFilter(){
        setInputType(InputType.TYPE_CLASS_PHONE);
        setFilters(new InputFilter[] { new InputFilter(){
            @Override
            public CharSequence filter(CharSequence source, int start, int end, android.text.Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
                    if (!resultingTxt.matches("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?")) {
                        return "";
                    }
                    else {
                        String[] splits = resultingTxt.split("\\.");
                        for (int i = 0; i < splits.length; i++) {
                            if (Integer.valueOf(splits[i]) > 255) {
                                return "";
                            }
                        }
                    }
                }
                return null;
            }
        }
        });

        addTextChangedListener(new TextWatcher(){
            boolean deleting = false;
            int lastCount = 0;

            @Override
            public void afterTextChanged(Editable s) {
                if (!deleting) {
                    String working = s.toString();
                    String[] split = working.split("\\.");
                    String string = split[split.length - 1];
                    if (string.length() == 3) {
                        s.append('.');
                        return;
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (lastCount < count) {
                    deleting = false;
                }
                else {
                    deleting = true;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing happens here
            }
        });
    }
}