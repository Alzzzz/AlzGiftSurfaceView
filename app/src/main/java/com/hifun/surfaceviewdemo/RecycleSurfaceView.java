package com.hifun.surfaceviewdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 */
public class RecycleSurfaceView extends SurfaceView implements SurfaceHolder.Callback,Runnable{
  
    private String TAG = this.getClass().getSimpleName();  
    private final SurfaceHolder mHolder;  
    private Thread mThread;
    private Thread mReadBitmapThread;
    private long mFrameSpaceTime = 60;  // 每帧图片的间隔时间
    private boolean mIsDraw = true;     // 画图开关  
    private int mCurrentIndext = 0;     // 当前正在播放的png
    private int[] resIds;

    private ConcurrentLinkedQueue<Bitmap> animBitmapQueue = new ConcurrentLinkedQueue<>();
    private int loadIndex;//载入的index
    private long curSize;//当前大小
    private long maxSize;//最大
    private boolean isFirst = true;
    private boolean loadComplete = false;

    public static int[] castles = new int[]{
            R.mipmap.castle_1,
            R.mipmap.castle_2,
            R.mipmap.castle_3,
            R.mipmap.castle_4,
            R.mipmap.castle_5,
            R.mipmap.castle_6,
            R.mipmap.castle_7,
            R.mipmap.castle_8,
            R.mipmap.castle_9,
            R.mipmap.castle_10,
            R.mipmap.castle_11,
            R.mipmap.castle_12,
            R.mipmap.castle_13,
            R.mipmap.castle_14,
            R.mipmap.castle_15,
            R.mipmap.castle_16,
            R.mipmap.castle_17,
            R.mipmap.castle_18,
            R.mipmap.castle_19,
            R.mipmap.castle_20,
            R.mipmap.castle_21,
            R.mipmap.castle_22,
            R.mipmap.castle_23,
            R.mipmap.castle_24,
            R.mipmap.castle_25,
            R.mipmap.castle_26,
            R.mipmap.castle_27,
            R.mipmap.castle_28,
            R.mipmap.castle_29,
            R.mipmap.castle_30,
            R.mipmap.castle_31,
            R.mipmap.castle_32,
            R.mipmap.castle_33,
            R.mipmap.castle_34,
            R.mipmap.castle_35,
            R.mipmap.castle_36,
            R.mipmap.castle_37,
            R.mipmap.castle_38,
            R.mipmap.castle_39,
            R.mipmap.castle_40,
            R.mipmap.castle_41,
            R.mipmap.castle_42,
            R.mipmap.castle_43,
            R.mipmap.castle_44,
            R.mipmap.castle_45,
            R.mipmap.castle_46,
            R.mipmap.castle_47,
            R.mipmap.castle_48,
            R.mipmap.castle_49,
            R.mipmap.castle_50,
            R.mipmap.castle_51,
            R.mipmap.castle_52,
            R.mipmap.castle_53,
            R.mipmap.castle_54,
            R.mipmap.castle_55,
            R.mipmap.castle_56,
            R.mipmap.castle_57,
            R.mipmap.castle_58,
            R.mipmap.castle_59,
            R.mipmap.castle_60,
            R.mipmap.castle_61,
            R.mipmap.castle_62,
            R.mipmap.castle_63,
            R.mipmap.castle_64,
            R.mipmap.castle_65,
            R.mipmap.castle_66,
            R.mipmap.castle_67,
            R.mipmap.castle_68,
            R.mipmap.castle_69,
            R.mipmap.castle_70,
            R.mipmap.castle_71,
            R.mipmap.castle_72,
            R.mipmap.castle_73,
            R.mipmap.castle_74,
            R.mipmap.castle_75,
            R.mipmap.castle_76,
            R.mipmap.castle_77,
            R.mipmap.castle_78,
            R.mipmap.castle_79,
            R.mipmap.castle_80,
            R.mipmap.castle_81,
            R.mipmap.castle_82,
            R.mipmap.castle_83,
            R.mipmap.castle_84,
            R.mipmap.castle_85,
            R.mipmap.castle_86,
            R.mipmap.castle_87
    };
  
    long gapTime;
    long lastTime;
  
    public RecycleSurfaceView(Context context) {this(context,null);}
  
    public RecycleSurfaceView(Context context, AttributeSet attrs) {this(context,attrs,0);}
  
    public RecycleSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);  
        mHolder = this.getHolder();       // 获得surfaceholder  
        mHolder.addCallback(this);        // 添加回调，这样三个方法才会执行  
  
        setZOrderOnTop(true);  
        mHolder.setFormat(PixelFormat.TRANSLUCENT);// 设置背景透明

        resIds = castles;
        maxSize = (int) (Runtime.getRuntime().maxMemory())/4;
    }
  
    /** 
    首先继承SurfaceView，并实现SurfaceHolder.Callback接口，实现它的三个方法： 
    surfaceCreated(SurfaceHolder holder)：surface创建的时候调用，一般在该方法中启动绘图的线程。 
    surfaceChanged(SurfaceHolder holder, int format, int width,int height)：surface尺寸发生改变的时候调用，如横竖屏切换。 
    surfaceDestroyed(SurfaceHolder holder) ：surface被销毁的时候调用，如退出游戏画面，一般在该方法中停止绘图线程。 
    还需要获得SurfaceHolder，并添加回调函数，这样这三个方法才会执行。 
    */  
  
    @Override  
    public void surfaceCreated(SurfaceHolder holder) {  
        Log.d(TAG, "surfaceCreated: ");
        if(castles == null){
            Log.e(TAG, "surfaceCreated: 图片资源为空");  
            return;  
        }  

        mReadBitmapThread = new Thread(readBitmapRunnable);
        mReadBitmapThread.start();

        mIsDraw = true;  
    }

    Runnable readBitmapRunnable = new Runnable() {
        @Override
        public void run() {
            while (resIds.length != loadIndex){
//                    Log.d(TAG, "animBitmapQueue.size() = "+animBitmapQueue.size()+
//                            " maxSize == "+maxSize+
//                            " curSize == "+curSize+
//                            " loadIndex == "+loadIndex);
                if (animBitmapQueue.size() < 20 && curSize < maxSize){
                    Bitmap tempBtm;
//                    if (isResource){
//                            Log.d(TAG, "curSize == "+curSize);
                    tempBtm = BitmapFactory.decodeResource(getResources(), resIds[loadIndex]);
//                    }else {
//                        tempBtm = fileToBitmap(paths.get(loadIndex));
//                    }
                    gapTime = System.currentTimeMillis() - lastTime;
                    lastTime = System.currentTimeMillis();
                    Log.d(TAG, "onNext load time = "+gapTime+"  loadIndex = "+loadIndex);
                    curSize += tempBtm.getByteCount();
                    animBitmapQueue.add(tempBtm);
                    loadIndex++;
                } else {
                    if (isFirst){
                        startFrams();
                        isFirst = false;
                    }
                }
            }
            loadComplete = true;
        }
    };

    private void startFrams() {
        mThread = new Thread(this);
        mThread.start();
    }


    @Override  
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {  
  
    }  
  
    @Override  
    public void surfaceDestroyed(SurfaceHolder holder) {  
        mIsDraw = false;  
        try {  
            Thread.sleep(mFrameSpaceTime);  
            Log.d(TAG, "surfaceDestroyed: Thread " + mThread.getState());  
        } catch (InterruptedException e) {  
            e.printStackTrace();  
        }  
    }  
  
    @Override  
    public void run() {
        synchronized (mHolder) {         // 这里加锁为了以后控制这个绘制线程的wait与notify
            while (mIsDraw) {  
                try {
                    drawView();  
                    Thread.sleep(mFrameSpaceTime);  
                } catch (InterruptedException e) {  
                    e.printStackTrace();  
                }  
            }  
        }  
    }


    BitmapDrawable drawable;
    private void drawView() {
        Log.i(TAG, "drawView: ");  
        Canvas mCanvas = mHolder.lockCanvas();      // 锁定画布
        try {
            drawable = new BitmapDrawable(getResources(), animBitmapQueue.poll());
            drawable.setBounds(0,0,getWidth(),getHeight());
            mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);        // 清除屏幕

            drawable.draw(mCanvas);
            curSize -= drawable.getBitmap().getByteCount();
//
//            if (mCurrentIndext == AnimSurfaceView.castles.length - 1) {
//                mCurrentIndext = 0;
//            }
        }
        catch (Exception e) {  
            e.printStackTrace();  
        }  
        finally {  
//            mCurrentIndext++;
            if (mCanvas != null) {  
                mHolder.unlockCanvasAndPost(mCanvas);       // 提交画布  
            }
            if (loadComplete && animBitmapQueue.size() <= 0){
                mIsDraw = false;
            }
            recycle(drawable.getBitmap());  // 这里回收资源非常重要！
        }
    }  
  
    private void recycle(Bitmap mBitmap) {  
        if(mBitmap != null)  
            mBitmap.recycle();  
    }  
}  