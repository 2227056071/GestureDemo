package com.example.gesturepoc;

import java.lang.ref.WeakReference;
import java.util.List;

import com.example.gesturepoc.LockPatternView.Cell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 验证手势密码界面
 */
public class VerifyActivity extends Activity {
    TextView textView;
    LockPatternView mLockPatternView;
    private List<Cell> lockPattern;

    private static class MyHandler extends Handler {
    }

    private final MyHandler mHandler = new MyHandler();

    public static class MyRunnable implements Runnable {
        private final WeakReference<Activity> mActivity;

        public MyRunnable(Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            Activity activity = mActivity.get();
            if (activity != null) {
                LockPatternView lockPatternView = (LockPatternView) activity.findViewById(R.id.lockview_verify);
                lockPatternView.clearPattern();
            }
        }
    }

    private MyRunnable mRunnable = new MyRunnable(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences(
                SettingActivity.LOCK, MODE_PRIVATE);
        String patternString = preferences.getString(SettingActivity.LOCK_KEY,
                null);
        if (patternString == null) {
            finish();
            return;
        }
        lockPattern = LockPatternView.stringToPattern(patternString);

        setContentView(R.layout.activity_verify);
        textView = (TextView) findViewById(R.id.textview_verify);
        mLockPatternView = (LockPatternView) findViewById(R.id.lockview_verify);
        mLockPatternView
                .setOnPatternListener(new LockPatternView.OnPatternListener() {

                    @Override
                    public void onPatternStart() {

                    }

                    @Override
                    public void onPatternDetected(List<Cell> pattern) {
                        if (pattern.equals(lockPattern)) {
                            // 验证手势密码成功
                            SharedPreferences preferences = getSharedPreferences(
                                    "remainingTimes", MODE_PRIVATE);
                            preferences.edit().putInt("times", 5).commit();
                            Toast.makeText(VerifyActivity.this,
                                    R.string.lockpattern_success,
                                    Toast.LENGTH_LONG).show();
                            mLockPatternView.clearPattern();
                            finish();
                        } else {
                            // 验证手势密码失败
                            mLockPatternView
                                    .setDisplayMode(LockPatternView.DisplayMode.Wrong);

                            SharedPreferences preferences = getSharedPreferences(
                                    "remainingTimes", MODE_PRIVATE);
                            int times = preferences.getInt("times", 0);
                            if (times <= 1) {
                                //  手势密码错误次数已达上限
                                AlertDialog.Builder builder = new AlertDialog.Builder(VerifyActivity.this);
                                builder.setTitle(R.string.tip);
                                builder.setMessage(R.string.changes_used_up);
                                builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        finish();
                                    }
                                });
                                builder.create().show();
                            } else {
                                preferences.edit().putInt("times", --times).commit();
                                textView.setText("密码绘制错误，您还有" + times
                                        + "次机会");
                                Animation shakeAnimation = AnimationUtils.loadAnimation(VerifyActivity.this, R.anim.shake);
                                textView.startAnimation(shakeAnimation);
                                textView.setTextColor(Color.parseColor("#c70c1e"));
                            }
                            mHandler.postDelayed(mRunnable, 1000);
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
