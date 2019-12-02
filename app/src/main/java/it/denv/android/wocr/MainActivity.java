package it.denv.android.wocr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {
    private static final String TESSERACT_LANGUAGE = "eng";
    private static final String TAG = "WOCR";
    private static final int CAMERA_REQUEST = 1000;

    private ImageView cameraPreview;
    private TessBaseAPI baseAPI;
    private String currentPhotoPath;
    private TextView pvrCode;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreview = findViewById(R.id.cameraPreview);
        pvrCode = findViewById(R.id.pvrCode);

        Button buttonTakePicture = findViewById(R.id.btn_take_pic);
        buttonTakePicture.setOnClickListener((action)->{
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    // Error occurred while creating the File
                }
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            getPackageName(),
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST);
                }
            }

        });


        baseAPI = new TessBaseAPI();

        try {
            Path p = Files.createTempDirectory("tesseract-");
            Path tessData = Files.createDirectories(Paths.get(p + "/tessdata"));

            Path enTessData = Files.createFile(
                    Paths.get(tessData + "/" + TESSERACT_LANGUAGE + ".traineddata")
            );

            BufferedInputStream bis = new BufferedInputStream(
                    getResources().openRawResource(R.raw.en)
            );

            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(
                    enTessData.toFile()
            ));

            Log.d(TAG, "enTessData=" + enTessData);

            while(bis.available() > 0){
                byte[] buffer = new byte[1024];
                int read = bis.read(buffer);

                bos.write(buffer, 0, read);
            }

            bos.close();

            String baseDirPath = p.toAbsolutePath().toString();
            Log.d(TAG, "baseDirPath=" + baseDirPath);

            baseAPI.setDebug(true);
            baseAPI.init(baseDirPath, "eng");
            baseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, ">+ 0123456789");
            baseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
            //baseAPI.setImage();

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST) {
            new Thread(()->{
                setPic();
                parseText(baseAPI.getUTF8Text());
                baseAPI.clear();
                File f = new File(currentPhotoPath);
                if(f.exists()){
                    f.delete();
                }
            }).start();
        }
    }

    private void parseText(String utf8Text) {
        for(String s : utf8Text.split("\n")){
            String noSpaces = s.replace(" ", "");
            if(noSpaces.length() == 52){
                Log.i(TAG, noSpaces);
                runOnUiThread(()-> pvrCode.setText(noSpaces));
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void setPic() {
        // Get the dimensions of the View
        int targetW = cameraPreview.getWidth();
        int targetH = cameraPreview.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Matrix m = new Matrix();
        m.postRotate(0);

        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        if(bitmap == null){
            Log.e(TAG, "Bitmap is null");
            return;
        }

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

        baseAPI.setImage(rotatedBitmap);
        runOnUiThread(()-> cameraPreview.setImageBitmap(rotatedBitmap));
    }

}
