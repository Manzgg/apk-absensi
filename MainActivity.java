package com.example.absensisiswa;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 100;
    private static final int REQ_CAMERA = 101;
    private static final String PREF = "absensi_v2";
    private static final String ADMIN_PIN = "123456"; // Ganti sebelum produksi.
    private static final int BLUE = 0xff1565c0;
    private static final int GREEN = 0xff2e7d32;
    private static final int RED = 0xffc62828;
    private static final int TEXT = 0xff202124;

    private SharedPreferences sp;
    private LocationManager lm;
    private LinearLayout root;
    private TextView status;
    private double schoolLat, schoolLon;
    private float radius;
    private String nis, name;
    private String pendingPhotoPath;
    private boolean waitingForLocation;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREF, MODE_PRIVATE);
        loadSettings();
        if (loggedIn()) showHome(); else showLogin();
    }

    private boolean loggedIn() { return !sp.getString("nis", "").isEmpty(); }
    private void loadSettings() {
        nis = sp.getString("nis", "");
        name = sp.getString("name", "");
        schoolLat = Double.longBitsToDouble(sp.getLong("lat", Double.doubleToLongBits(-6.9175)));
        schoolLon = Double.longBitsToDouble(sp.getLong("lon", Double.doubleToLongBits(107.6191)));
        radius = sp.getFloat("radius", 100f);
    }

    private TextView tv(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(TEXT);
        t.setPadding(16, 12, 16, 12); return t;
    }
    private TextView title(String s) {
        TextView t = tv(s, 25); t.setTextColor(BLUE); t.setGravity(Gravity.CENTER); t.setPadding(8, 12, 8, 18); return t;
    }
    private Button btn(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); return b;
    }
    private void base(String titleText) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20, 20, 20, 20);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        root.addView(title(titleText));
    }
    private EditText field(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16); root.addView(e); return e; }

    private void showLogin() {
        base("ABSENSI SISWA");
        root.addView(tv("Absensi berbasis lokasi sekolah", 16));
        root.addView(tv("Masukkan data siswa untuk masuk. Data login tersimpan di perangkat ini.", 13));
        EditText eNis = field("NIS / NISN");
        EditText eName = field("Nama lengkap siswa");
        Button masuk = btn("Masuk ke Aplikasi"); root.addView(masuk);
        root.addView(tv("Versi 2.0 • GPS + Geofence + Selfie", 12));
        masuk.setOnClickListener(v -> {
            String n = eNis.getText().toString().trim(); String nm = eName.getText().toString().trim();
            if (n.isEmpty() || nm.isEmpty()) { toast("NIS/NISN dan nama wajib diisi."); return; }
            sp.edit().putString("nis", n).putString("name", nm).apply(); nis=n; name=nm; showHome();
        });
    }

    private void showHome() {
        base("Halo, " + name);
        status = tv("", 15); root.addView(status);
        TextView school = tv("📍 Area sekolah: radius " + Math.round(radius) + " meter\nKoordinat: " + schoolLat + ", " + schoolLon, 13);
        root.addView(school);

        Button ab = btn("📷  ABSEN HADIR (SELFIE + GPS)"); root.addView(ab);
        Button izin = btn("📝  AJUKAN IZIN / SAKIT"); root.addView(izin);
        Button ri = btn("📋  RIWAYAT ABSENSI"); root.addView(ri);
        Button admin = btn("⚙  PENGATURAN ADMIN"); root.addView(admin);
        Button out = btn("Keluar dari akun"); root.addView(out);
        updateStatus();
        ab.setOnClickListener(v -> startSelfie());
        izin.setOnClickListener(v -> submitNonPresent());
        ri.setOnClickListener(v -> showHistory());
        admin.setOnClickListener(v -> adminPin());
        out.setOnClickListener(v -> { sp.edit().remove("nis").remove("name").apply(); showLogin(); });
    }

    private void updateStatus() {
        String d = today(); String rec = sp.getString("att_" + d, "");
        if (rec.isEmpty()) { status.setText("● Belum melakukan absensi hari ini"); status.setTextColor(RED); }
        else { status.setText("✓ " + rec.replace("|", "•")); status.setTextColor(GREEN); }
    }

    private void startSelfie() {
        if (!sp.getString("att_" + today(), "").isEmpty()) { toast("Anda sudah memiliki absensi hari ini."); return; }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA); return;
        }
        try { startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA); }
        catch (Exception e) { toast("Kamera tidak tersedia di perangkat ini."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            if (photo != null) { pendingPhotoPath = savePhoto(photo); absenWithLocation(); }
            else toast("Foto selfie tidak terbaca.");
        } else if (requestCode == REQ_CAMERA) toast("Selfie dibatalkan. Absensi tidak diproses.");
    }

    private String savePhoto(Bitmap b) {
        String filename = "selfie_" + System.currentTimeMillis() + ".jpg";
        File f = new File(getFilesDir(), filename);
        try (FileOutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 82, out); return f.getAbsolutePath(); }
        catch (Exception e) { toast("Gagal menyimpan selfie."); return null; }
    }

    private void absenWithLocation() {
        if (pendingPhotoPath == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            waitingForLocation = true; requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION); return;
        }
        if (!lmReady()) return;
        status.setText("⏳ Mendeteksi lokasi GPS..."); status.setTextColor(BLUE);
        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                try { lm.removeUpdates(this); } catch (Exception ignored) {}
                waitingForLocation = false;
                if (android.os.Build.VERSION.SDK_INT >= 31 && l.isMock()) { failPhoto("Lokasi palsu/mock terdeteksi."); return; }
                double d = distance(schoolLat, schoolLon, l.getLatitude(), l.getLongitude());
                if (d <= radius) {
                    String rec = now() + " | HADIR | " + Math.round(d) + " m | selfie";
                    sp.edit().putString("att_" + today(), rec).putString("photo_" + today(), pendingPhotoPath).apply();
                    pendingPhotoPath = null; updateStatus(); toast("Absensi berhasil disimpan.");
                } else failPhoto("Di luar area sekolah. Jarak Anda " + Math.round(d) + " m, batas " + Math.round(radius) + " m.");
            }
            @Override public void onProviderDisabled(String provider) { failPhoto("GPS dimatikan."); }
        };
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, listener);
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null && System.currentTimeMillis() - last.getTime() < 60000) listener.onLocationChanged(last);
        } catch (SecurityException e) { toast("Izin lokasi ditolak."); }
    }

    private void failPhoto(String message) { pendingPhotoPath = null; status.setText("✕ " + message); status.setTextColor(RED); toast("Absensi ditolak: " + message); }
    private boolean lmReady() {
        lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) { toast("Aktifkan GPS/lokasi terlebih dahulu."); return false; }
        return true;
    }

    private void submitNonPresent() {
        if (!sp.getString("att_" + today(), "").isEmpty()) { toast("Absensi hari ini sudah tercatat."); return; }
        final String[] choices = {"Izin", "Sakit"};
        new AlertDialog.Builder(this).setTitle("Jenis pengajuan").setItems(choices, (d, which) -> askReason(choices[which])).show();
    }
    private void askReason(String type) {
        EditText reason = new EditText(this); reason.setHint("Alasan (wajib)"); reason.setMinLines(3);
        new AlertDialog.Builder(this).setTitle(type).setView(reason).setNegativeButton("Batal", null).setPositiveButton("Simpan", (d,w) -> {
            String r = reason.getText().toString().trim(); if (r.isEmpty()) { toast("Alasan wajib diisi."); return; }
            String rec = now() + " | " + type.toUpperCase(Locale.US) + " | " + r;
            sp.edit().putString("att_" + today(), rec).putString("photo_" + today(), "").apply(); updateStatus(); toast("Pengajuan tersimpan di perangkat.");
        }).show();
    }

    private void showHistory() {
        base("Riwayat 90 Hari");
        StringBuilder s = new StringBuilder();
        for (int i=0;i<90;i++) {
            Calendar c=Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR,-i); String d=fmt("yyyy-MM-dd",c.getTime());
            String r=sp.getString("att_"+d,""); if(!r.isEmpty()) s.append("\n").append(d).append("\n").append(r.replace("|"," • ")).append("\n");
        }
        root.addView(tv(s.length()==0?"Belum ada data absensi.":s.toString(),15));
        Button back=btn("Kembali"); root.addView(back); back.setOnClickListener(v->showHome());
    }

    private void adminPin() {
        EditText e=new EditText(this); e.setHint("PIN admin"); e.setInputType(2|16);
        new AlertDialog.Builder(this).setTitle("Akses Admin").setMessage("Gunakan PIN admin untuk mengubah geofence.").setView(e).setNegativeButton("Batal",null).setPositiveButton("Masuk",(d,w)->{
            if(ADMIN_PIN.equals(e.getText().toString())) showSettings(); else toast("PIN admin salah.");
        }).show();
    }
    private void showSettings() {
        base("Pengaturan Geofence");
        root.addView(tv("Tentukan titik tengah sekolah dan radius maksimal absensi. Untuk mendapatkan koordinat, gunakan Google Maps atau titik GPS sekolah.",13));
        EditText eLat=field("Latitude sekolah"); eLat.setText(String.valueOf(schoolLat));
        EditText eLon=field("Longitude sekolah"); eLon.setText(String.valueOf(schoolLon));
        EditText eRad=field("Radius meter (contoh 100)"); eRad.setText(String.valueOf(radius));
        Button save=btn("Simpan Pengaturan"); root.addView(save);
        Button back=btn("Kembali"); root.addView(back);
        save.setOnClickListener(v->{ try { double la=Double.parseDouble(eLat.getText().toString()); double lo=Double.parseDouble(eLon.getText().toString()); float ra=Float.parseFloat(eRad.getText().toString()); if(la<-90||la>90||lo<-180||lo>180||ra<10||ra>5000) throw new IllegalArgumentException(); schoolLat=la;schoolLon=lo;radius=ra;sp.edit().putLong("lat",Double.doubleToLongBits(la)).putLong("lon",Double.doubleToLongBits(lo)).putFloat("radius",ra).apply();toast("Pengaturan geofence disimpan.");showHome(); } catch(Exception x){toast("Koordinat/radius tidak valid.");} });
        back.setOnClickListener(v->showHome());
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_LOCATION && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED && waitingForLocation) absenWithLocation();
        else if(r==REQ_LOCATION) toast("Izin lokasi diperlukan untuk absensi.");
        else if(r==REQ_CAMERA && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) startSelfie();
        else if(r==REQ_CAMERA) toast("Izin kamera diperlukan untuk selfie absensi.");
    }

    private String today(){return fmt("yyyy-MM-dd",new Date());}
    private String now(){return fmt("dd/MM/yyyy HH:mm:ss",new Date());}
    private String fmt(String pattern,Date date){return new SimpleDateFormat(pattern,Locale.US).format(date);}
    private double distance(double a,double b,double c,double d){float[] r=new float[1];Location.distanceBetween(a,b,c,d,r);return r[0];}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
