package cl.coders.faketraveler;

import android.text.Editable;
import android.text.TextWatcher;

public abstract class SimpleTextWatcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // default kosong
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // default kosong
    }

    @Override
    public abstract void afterTextChanged(Editable s);
}
