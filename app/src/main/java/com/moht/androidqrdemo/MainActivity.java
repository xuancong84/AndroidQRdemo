package com.moht.androidqrdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.Callable;

public class MainActivity extends AppCompatActivity {

	public static final int BARCODE_READER_REQUEST_CODE = 1000;
	public ClipboardManager clipboardManager;
	private TextView textView;
	private Context mContext;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = getApplicationContext();
		clipboardManager = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
		setContentView(R.layout.activity_main);
		textView = findViewById(R.id.QRcontent);
	}

	public synchronized void singleScanButtonPressed(View view) {
		BarcodeCaptureActivity.checkQR = new Callable<Boolean>() {
			public Boolean call() { return true; } };
		Intent intent = new Intent( mContext, BarcodeCaptureActivity.class );
		startActivityForResult( intent, BARCODE_READER_REQUEST_CODE );
	}

	public synchronized void continuousScanQrButtonPressed(View view) {
		BarcodeCaptureActivity.checkQR = new Callable<Boolean>() {
			public Boolean call() {
				runOnUiThread(new Runnable() {
					public void run() {
						Toast.makeText(mContext, BarcodeCaptureActivity.scan_result, Toast.LENGTH_SHORT).show();
					} } );
				return false;
			} };
		Intent intent = new Intent( mContext, BarcodeCaptureActivity.class );
		startActivityForResult( intent, BARCODE_READER_REQUEST_CODE );
	}

	public synchronized void copyToClipboard(View view){
		String txt = (String)textView.getText();
		ClipData clip = ClipData.newPlainText(txt, txt);
		clipboardManager.setPrimaryClip(clip);
		Toast.makeText( mContext, "Text copied to clipboard", Toast.LENGTH_SHORT ).show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case BARCODE_READER_REQUEST_CODE:
				// TODO: implement QR on-detect handler
				textView.setText( data==null ? BarcodeCaptureActivity.scan_result :
						data.getStringExtra(BarcodeCaptureActivity.BarcodeObject) );
				break;
			default:
				break;
		}
	}
}
