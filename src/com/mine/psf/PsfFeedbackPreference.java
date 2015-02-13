package com.mine.psf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;


public class PsfFeedbackPreference extends Preference {

  private final String[] EMAIL_ADDR = {"mine260309@gmail.com"};
  private final String EMAIL_SUBJECT = "Feedback of MinePsfPlayer";

  private Context context;

  public PsfFeedbackPreference(Context c) {
    super(c);
    context = c;
  }

  public PsfFeedbackPreference(Context c, AttributeSet attrs) {
    super(c, attrs);
    context = c;
  }

  public PsfFeedbackPreference(Context c, AttributeSet attrs,
                               int defStyle) {
    super(c, attrs, defStyle);
    context = c;
  }

  @Override
  protected void onClick() {
    Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
    emailIntent.setType("message/rfc822");
    emailIntent.putExtra(Intent.EXTRA_EMAIL, EMAIL_ADDR);
    emailIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
    PackageManager pm = context.getPackageManager();
    PackageInfo pi;
    try {
      pi = pm.getPackageInfo(context.getPackageName(), 0);
    } catch (NameNotFoundException e) {
      Log.v("Feedback", "Unable to get package info!");
      pi = new PackageInfo(); // return a empty package info
    }

    String texts = "Device Information: " + android.os.Build.MODEL + " " +
        android.os.Build.DEVICE + " SDK " +
        android.os.Build.VERSION.SDK +
        ", Software version: " + pi.versionName +
        "\n*** If you don't want to share your device information," +
        " you can just delete this text :)\n";
    emailIntent.putExtra(Intent.EXTRA_TEXT, texts);

    try {
      context.startActivity(Intent.createChooser(emailIntent, "Feedback via email..."));
    } catch (android.content.ActivityNotFoundException ex) {
      Toast.makeText(context, "There are no email clients installed.",
          Toast.LENGTH_SHORT).show();
    }
  }
}
