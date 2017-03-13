package com.example.gesturepoc;

import java.util.ArrayList;
import java.util.List;

import com.example.gesturepoc.LockPatternView.Cell;
import com.example.gesturepoc.LockPatternView.DisplayMode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置手势密码界面
 */
public class SettingActivity extends Activity {
    boolean mIsFirstFinshed = false;
    TextView textView;
    LockPatternView mLockPatternView;
    LockPatternView mSmallPatternView;
    private List<Cell> mChoosePattern;
    final static String LOCK = "lock";
    public static final String LOCK_KEY = "lock_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        textView = (TextView) findViewById(R.id.textview);
        mLockPatternView = (LockPatternView) findViewById(R.id.lockView_setting);
        mLockPatternView.setDisplayMode(DisplayMode.Wrong);
        mSmallPatternView = (LockPatternView) findViewById(R.id.lockView_small);
        mLockPatternView
                .setOnPatternListener(new LockPatternView.OnPatternListener() {

                    @Override
                    public void onPatternStart() {

                    }

                    @Override
                    public void onPatternDetected(List<Cell> pattern) {
                        if (pattern.size() < 4) {
                            // 密码少于4位
                            Animation shakeAnimation = AnimationUtils
                                    .loadAnimation(SettingActivity.this,
                                            R.anim.shake);
                            textView.setText(R.string.lockpattern_recording_incorrect_too_short);
                            textView.setTextColor(Color.parseColor("#c70c1e"));
                            textView.startAnimation(shakeAnimation);
                            mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                            mLockPatternView.clearPattern();
                            return;
                        }
                        if (!mIsFirstFinshed) {
                            // 第一次绘制手势密码
                            mChoosePattern = new ArrayList<LockPatternView.Cell>(
                                    pattern);
                            mIsFirstFinshed = true;
                            String patternString = LockPatternView
                                    .patternToString(mChoosePattern);
                            mSmallPatternView.setPattern(DisplayMode.Correct, mChoosePattern);

                            textView.setText(R.string.confirm_again);
                            mLockPatternView.clearPattern();
                            return;
                        }
                        if (mChoosePattern.equals(pattern)) {
                            // 成功设置了手势密码
                            Toast.makeText(SettingActivity.this,
                                    R.string.lockpattern_setting_success,
                                    Toast.LENGTH_LONG).show();
                            SharedPreferences preferences = getSharedPreferences(
                                    SettingActivity.LOCK, MODE_PRIVATE);
                            preferences
                                    .edit()
                                    .putString(
                                            SettingActivity.LOCK_KEY,
                                            LockPatternView
                                                    .patternToString(mChoosePattern))
                                    .commit();
                            SharedPreferences preferencesTimes = getSharedPreferences(
                                    "remainingTimes", MODE_PRIVATE);
                            preferencesTimes.edit().putInt("times", 5).commit();
                            mLockPatternView.clearPattern();
                            finish();
                        } else {
                            // 与上次绘制不一致
                            Animation shakeAnimation = AnimationUtils
                                    .loadAnimation(SettingActivity.this,
                                            R.anim.shake);
                            textView.startAnimation(shakeAnimation);
                            textView.setText(R.string.try_paint_again);
                            textView.setTextColor(Color.parseColor("#c70c1e"));
                            mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                            mLockPatternView.clearPattern();
                        }

                    }

                    @Override
                    public void onPatternCleared() {

                    }

                    @Override
                    public void onPatternCellAdded(List<Cell> pattern) {

                    }
                });
    }
}
