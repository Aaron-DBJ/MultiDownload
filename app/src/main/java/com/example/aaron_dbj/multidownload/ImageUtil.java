package com.example.aaron_dbj.multidownload;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.File;

public class ImageUtil{

    public static Bitmap zoomImage(File file, int newWidth, int newHeight){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        int originalWidth = options.outWidth;
        int originalHeight = options.outHeight;

        options.inSampleSize = getSimpleSize(newWidth,newHeight,originalWidth,originalHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(),options);
    }

    private static int getSimpleSize(int newWidth,int newHeight, int originalWidth,int originalHeight){
        int simpleSize = 1;
        if (originalWidth>newWidth && originalHeight>newWidth){
            simpleSize = originalWidth/newWidth;
        }else if (originalHeight>newHeight && originalWidth>newHeight){
            simpleSize = originalHeight/newHeight;
        }
        if (simpleSize<=0){
            simpleSize = 1;
        }
        Log.d("inSimpleSize",""+simpleSize);
        return simpleSize;
    }

}
