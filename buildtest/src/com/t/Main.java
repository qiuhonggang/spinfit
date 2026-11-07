package com.t; import android.app.Activity; import android.os.Bundle; import android.widget.TextView;
public class Main extends Activity { public void onCreate(Bundle b){ super.onCreate(b); TextView t=new TextView(this); t.setText("Hello"); setContentView(t);} }
