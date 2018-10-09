package com.example.aaron_dbj.multidownload;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private Button download;
    private EditText fileUrl;
    private int total;
    private boolean downloading = false;
    private URL url;
    private File file;
    private List<HashMap<String,Integer>> threadList;
    private ProgressBar progressBar;
    private ImageView image;
    private Bitmap bitmap;
    private String imageFile;
    private int length;//文件长度(大小)
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE };

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 0){
                progressBar.setProgress(msg.arg1);
                if (msg.arg1 == length+2){
                    Toast.makeText(MainActivity.this,"下载完成",Toast.LENGTH_LONG).show();
                    download.setText("下载");
                }
            }

            return false;
        }
    });
    public static void verifyStoragePermissions(Activity activity) {

        if (ActivityCompat.checkSelfPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE)
                !=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(activity,PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        threadList = new ArrayList<>();
        File imagePath = new File(Environment.getExternalStorageDirectory(),"fireworks.jpg");

        image.setImageBitmap(ImageUtil.zoomImage(imagePath,200,200));
        verifyStoragePermissions(MainActivity.this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        if (downloading){
            downloading = false;
            download.setText("下载");
            return;
        }
        downloading = true;
        download.setText("暂停");
        //第一次开始下载，中途没暂停过
        if (threadList.size()==0) {
            //下载文件是耗时操作，必须新开线程进行操作，不能在主线程操作
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        url = new URL(fileUrl.getText().toString());
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(5000);
                        connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible;MSIE 8.0;Windows NT 5.0;)");
                        //获取文件长度
                        length = connection.getContentLength();
                        Log.d("Content Length",""+length);
                        progressBar.setMax(length);//进度条最大长度
                        progressBar.setProgress(0);//进度条当前进度

                        /**
                         * 判定是否成功获取文件，可以通过得到的文件长度判断，也可以通过状态码判断。
                         * 如 if (connection.getResponseCode()==200),200表示请求成功。
                         */
                        if (length < 0) {
                            /**
                             * 注意：这里有个问题极容易出错，就是Toast（）方法只能在主线程执行，而在子线程
                             * 不能执行，必须先使用Looper.prepare()，然后再用Looper.loop();
                             */
                            Looper.prepare();
                            Toast.makeText(MainActivity.this, "文件不存在", Toast.LENGTH_SHORT).show();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    download.setText("下载");
                                }
                            });
                            Looper.loop();
                            return;
                        }

                        file = new File(Environment.getExternalStorageDirectory(), getFileName(fileUrl.getText().toString()));
                        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                        /**
                         * 多线程断点下载文件的思想就是：讲一个大文件分为几个小部分，为每一个部分的文件创建一个
                         * 线程来下载；每个小部分文件下载完毕后，通过RandomAccessFile类将各个部分文件合并为
                         * 一个完整文件，断点下载是考该类实现的。
                         */
                        randomAccessFile.setLength(length);
                        /**
                         * 比如说该demo是把一个文件分为三个部分下载，最后在合并.注意除3是除不尽的，
                         * 可能会有遗漏，所以在第三个部分的文件时，直接将下载的长度终点设为length，
                         * 起点为第二个文件的终点。
                         */
                        int blockSize = length / 3;
                        //给每一个部分都开辟一个线程去下载
                        for (int i = 0; i < 3; i++) {
                            int begin = i * blockSize;//第i个子部分下载起始点
                            int end = (i + 1) * blockSize;//第i个子部分下载结束点，也是第i+1个子部分下载起始点
                            if (i == 2) {
                                end = length;
                            }
                            //保存断点信息，即下载的开始位置和结束位置，以及已经下载的大小
                            HashMap<String,Integer> map = new HashMap<>();
                            map.put("begin",begin);
                            map.put("end",end);
                            map.put("finished",0);//设置已经下载的大小为0
                            threadList.add(map);
                            //创建新线程，下载文件

                            Thread thread = new Thread(new DownLoadRunnable(begin, end, url, file, i));
                            thread.start();

                        }

                    } catch (MalformedURLException e) {
                        Looper.prepare();
                        Toast.makeText(MainActivity.this, "URL不正确", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }).start();
        }else {
            //恢复下载
            for (int i =0;i<threadList.size();i++){
                HashMap<String,Integer> map = threadList.get(i);
                int begin = map.get("begin")+map.get("finished");
                int end = map.get("end");

                Thread thread = new Thread(new DownLoadRunnable(begin,end,url,file,i));
                thread.start();
            }
        }

    }
    private void initView(){
        download = findViewById(R.id.download);
        fileUrl = findViewById(R.id.file_url);
        progressBar = findViewById(R.id.progress);
        image = findViewById(R.id.imageView);
        download.setOnClickListener(this);

    }
    private String getFileName(String url){
        int index = url.lastIndexOf("/")+1;
        return url.substring(index);
    }

    //具体实现下载的类
    class DownLoadRunnable implements Runnable{
        private int begin,end;//文件下载的开始点和结束点
        private URL url;
        private File file;
        private int id;//文件id，标志文件的。

        public DownLoadRunnable(int begin,int end,URL url,File file,int id){
            this.begin = begin;
            this.end = end;
            this.url = url;
            this.file = file;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                if (begin>end){
                    return;
                }
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent","Mozilla/4.0(compatible;MSIE 8.0;Windows NT 5.0;)");
                /**
                 * http协议头部有个range：bytes = x - y，表示文件内容从第x个字节到第y个字节，
                 * 所以分割文件就是利用它来实现，我们可以自己定义x和y，也即是下载的开始位置和
                 * 结束位置
                 */
                connection.setRequestProperty("Range","bytes="+begin+"-"+end);
                RandomAccessFile randomAccessFile = new RandomAccessFile(file,"rw");
                randomAccessFile.seek(begin);
                InputStream inputStream = connection.getInputStream();
                byte[] buf = new byte[1024*1024];
                int len = 0;
                HashMap<String,Integer> map = threadList.get(id);
                while ((len = inputStream.read(buf))!=-1&&downloading){
                    randomAccessFile.write(buf,0,len);
                    map.put("finished",map.get("finished")+len);
                    updateProgress(len);
                    Log.d("Download length:",""+total);
                }

                inputStream.close();
                randomAccessFile.close();

            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //更新下载文件的长度
    synchronized public void updateProgress(int add){
        total+=add;
        Message message = handler.obtainMessage(0,total,0);
        handler.sendMessage(message);

    }

}
