package android.ubicuas.unicauca.notasadhesivas;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class ActNotasAdhesivas extends AppCompatActivity implements EscribirFragment.OnFragmentInteractionEscribirListener, P2PFragment.OnFragmentP2PInteractionListener {

    LeerFragment fragmentLeer;
    EscribirFragment fragmentEscribir;
    P2PFragment fragmentP2P;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private static final String TAG = "notasadhesivas";
    private boolean mResumed = false;
    private boolean mWriteMode = false;
    NfcAdapter mNfcAdapter;
    PendingIntent mNfcPendingIntent;
    IntentFilter[] mWriteTagFilters;
    IntentFilter[] mNdefExchangeFilters;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_act_notas_adhesivas);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        PackageManager pm = this.getPackageManager();
        // Check whether NFC is available on device
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            // NFC is not available on the device.
            Toast.makeText(this, "The device does not has NFC hardware.",
                    Toast.LENGTH_SHORT).show();
        }
        // Check whether device is running Android 4.1 or higher
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            // Android Beam feature is not supported.
            Toast.makeText(this, "Android Beam is not supported.",
                    Toast.LENGTH_SHORT).show();
        }
        else {
            // NFC and Android Beam file transfer is supported.
            Toast.makeText(this, "Android Beam is supported on your device.",
                    Toast.LENGTH_SHORT).show();
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
//        findViewById(R.id.write_tag).setOnClickListener(mTagWriter);
        //mNote = ((EditText) findViewById(R.id.editText));
        //mNote.addTextChangedListener(mTextWatcher);
        // Gestiona los intents NFC en esta actividad.
        mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        // Intentfilters para leer el mensaje de una etiqueta o intercambiarlo sobre p2p.
        IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndefDetected.addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) {
        }
        mNdefExchangeFilters = new IntentFilter[] { ndefDetected };

        // Intentfilters para escribir una etiqueta.
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        mWriteTagFilters = new IntentFilter[] { tagDetected };

    }

    public void sendFile() {

        // Check whether NFC is enabled on device
        if(!mNfcAdapter.isEnabled()){
            // NFC is disabled, show the settings UI
            // to enable NFC
            Toast.makeText(this, "Please enable NFC.",
                    Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        }
        // Check whether Android Beam feature is enabled on device
        else if(!mNfcAdapter.isNdefPushEnabled()) {
            // Android Beam is disabled, show the settings UI
            // to enable Android Beam
            Toast.makeText(this, "Please enable Android Beam.",
                    Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_NFCSHARING_SETTINGS));
        }
        else {
            // NFC and Android Beam both are enabled

            // File to be transferred
            // For the sake of this tutorial I've placed an image
            // named 'wallpaper.png' in the 'Pictures' directory
            String fileName = "wallpaper.png";

            // Retrieve the path to the user's public pictures directory
            File fileDirectory = Environment
                    .getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES);

            // Create a new file using the specified directory and name
            File fileToTransfer = new File(fileDirectory, fileName);
            fileToTransfer.setReadable(true, false);
            mNfcAdapter.setBeamPushUris(new Uri[]{Uri.fromFile(fileToTransfer)}, this);
        }
    }


    protected void onNewIntent(Intent intent) {
        // Modo de intercambio NDEF
        if (!mWriteMode && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            NdefMessage[] msgs = getNdefMessages(intent);
            if (fragmentLeer!=null){
                fragmentLeer.promptForContent(msgs[0]);
            }

        }

        // Modo de escritura de etiquetas
        if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTag(getNoteAsNdef(), detectedTag);
        }

    }

    protected void onPause() {
        super.onPause();
        mResumed = false;
        mNfcAdapter.disableForegroundNdefPush(this);
    }

    protected void onResume() {
        super.onResume();
        mResumed = true;
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            NdefMessage[] messages = getNdefMessages(getIntent());
            byte[] payload = messages[0].getRecords()[0].getPayload();
            //setNoteBody(new String(payload));
            setIntent(new Intent());
        }
        enableNdefExchangeMode();
    }

    NdefMessage[] getNdefMessages(Intent intent) {
        // Procesamiento del intent
        NdefMessage[] msgs = null;
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i <rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            } else {
                // Tipo de etiquetadesconocido
                byte[] empty = new byte[] {};
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty);
                NdefMessage msg = new NdefMessage(new NdefRecord[] {
                        record
                });
                msgs = new NdefMessage[] {
                        msg
                };
            }
        } else {
            Log.d(TAG, "Unknown intent.");
            finish();
        }
        return msgs;
    }




    private void enableNdefExchangeMode() {
        mNfcAdapter.enableForegroundNdefPush(ActNotasAdhesivas.this, getNoteAsNdef());
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mNdefExchangeFilters, null);
    }

    private NdefMessage getNoteAsNdef() {
        byte[] textBytes;
        if (fragmentEscribir!=null){
            textBytes = fragmentEscribir.mNote.getText().toString().getBytes();
        }else{
            textBytes = "".toString().getBytes();
        }

        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "text/plain".getBytes(),
                new byte[] {}, textBytes);
        return new NdefMessage(new NdefRecord[] {
                textRecord
        });
    }



    boolean writeTag(NdefMessage message, Tag tag) {
        int size = message.toByteArray().length;

        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();

                if (!ndef.isWritable()) {
                    toast("La etiqueta es de sólo lectura.");
                    return false;
                }
                if (ndef.getMaxSize() < size) {
                    toast("La capacidad de la etiqueta es de " + ndef.getMaxSize()
                            + " bytes, el mensaje es de " + size + " bytes.");
                    return false;
                }

                ndef.writeNdefMessage(message);
                toast("Mensaje escrito sobre la etiqueta pre formateada.");
                return true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        toast("Etiquetaformateada y mensajeescrito.");
                        return true;
                    } catch (IOException e) {
                        toast("Formato de etiquetafallido.");
                        return false;
                    }
                } else {
                    toast("La etiqueta no soporta NDEF.");
                    return false;
                }
            }
        } catch (Exception e) {
            toast("Falló proceso de escritura.");
        }

        return false;
    }


    private void disableNdefExchangeMode() {
        mNfcAdapter.disableForegroundNdefPush(this);
        mNfcAdapter.disableForegroundDispatch(this);
    }

    private void enableTagWriteMode() {
        mWriteMode = true;
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        mWriteTagFilters = new IntentFilter[] {
                tagDetected
        };
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);
    }

    private void disableTagWriteMode() {
        mWriteMode = false;
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onFragmentInteractionDisabled() {
        disableNdefExchangeMode();
        enableTagWriteMode();
    }

    @Override
    public void onFragmentInteractionEnabled() {
        disableTagWriteMode();
        enableNdefExchangeMode();
    }

    @Override
    public void onFragmentInteractionSend() {
        sendFile();
    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            Fragment fragment = null;
            switch (position) {
                case 0:
                    if (fragmentLeer==null) {
                        fragmentLeer = new LeerFragment();
                    }
                    fragment = fragmentLeer;
                    break;
                case 1:
                    if (fragmentEscribir==null) {
                        fragmentEscribir = new EscribirFragment();
                    }
                    fragment = fragmentEscribir;
                    break;
                case 2:
                    if (fragmentP2P==null) {
                        fragmentP2P = new P2PFragment();
                    }
                    fragment = fragmentP2P;
                    break;
                default:
                    break;
            }
            return fragment;

        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Leer";
                case 1:
                    return "Escribir";
                case 2:
                    return "P2P";
            }
            return null;
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


}
