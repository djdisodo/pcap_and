package com.gitlab.djdisodo.ksp.packetcapturedemo;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;
import java.io.OutputStream;

public class CaptureService extends VpnService {
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Builder builder = new Builder();
		builder.setBlocking(true);
		builder.setMtu(1500);
		builder.addAddress("10.5.37.1", 16);
		builder.addRoute("0.0.0.0", 0);
		try {
			builder.addAllowedApplication("com.sollae.eztcpclient");
		} catch (Exception e) {
			e.printStackTrace();
		}

		ParcelFileDescriptor fileDescriptor = builder.establish();
		InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor);
		OutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(fileDescriptor);
		new SocketReaderThread(inputStream, outputStream).start();
		return Service.START_STICKY;
	}
}
