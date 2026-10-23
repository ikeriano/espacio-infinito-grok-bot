package com.ikeriano.espacioinfinito;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { AutoJob.schedule(c); }
}
