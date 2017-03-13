package com.example.gesturepoc;

import android.view.View;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.widget.Button;

public class MainActivity extends Activity implements View.OnClickListener {
	Button button_setting;
	Button button_verify;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		View button_setting = (Button) findViewById(R.id.button_setting);
		button_verify = (Button) findViewById(R.id.button_verify);
		button_setting.setOnClickListener(this);
		button_verify.setOnClickListener(this);
	}

	@Override
	public void onClick(View arg0) {
		switch (arg0.getId()) {
		case R.id.button_setting:
			startActivity(new Intent(MainActivity.this, SettingActivity.class));
			break;

		default:
			startActivity(new Intent(MainActivity.this, VerifyActivity.class));
			break;
		}
	}

}
