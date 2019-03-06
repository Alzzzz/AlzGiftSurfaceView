package com.starunion.hefantv.view;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.TextureView;

import com.starunion.hefanim.common.HefanIMConstants;
import com.starunion.hefanim.ihefan.IHeFanMessage;
import com.starunion.hefantv.util.L;
import com.starunion.hefantv.util.PresentConstans;
import com.starunion.hefantv.util.ScreenUtils;
import com.starunion.hefantv.util.ZIP;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 描述:大动画礼物
 *
 * @author Suther
 */
public class AnimationSurfaceView extends TextureView implements TextureView.SurfaceTextureListener, Runnable {
    private String TAG = this.getClass().getSimpleName();
    private boolean isDebug = false;
    //    private final SurfaceHolder mHolder;
//    private SurfaceTexture surfaceTexture;
    private Thread mThread;
    private Thread mReadBitmapThread;
    private long mFrameSpaceTime = 80;  // 每帧图片的间隔时间
    private int[] resIds;

    private int screenWidth = 0;//屏幕宽
    private int screenHeight = 0;//屏幕高

    private ConcurrentLinkedQueue<Bitmap> animBitmapQueue = new ConcurrentLinkedQueue<>();
    private int loadIndex;//载入的index
    private long curSize;//当前大小
    private long maxSize;//最大
    private volatile boolean drawPermission = true;//是否有可以画画的权限
    private volatile boolean readFile2Bitmap = true;    //读图开关
    private volatile boolean canDrawBtmOnCanvas = true;     // 画图开关
    private volatile boolean isStop = false;    //是否在后台
    private boolean loadComplete = false;

    private boolean isResource = false;

    long gapTime;
    long lastTime;

    AnimationCallback animationCallback;
    IHeFanMessage message;
    Bitmap mCanRecycleBtm;
    int bmpCount = 0;

    long mDrawTime = 0;

    private String giftPath;

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        setAlpha(0.99f);
        Canvas clearCanvas = this.lockCanvas();
        if (clearCanvas != null) {
            clearCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);        // 清除屏幕
            this.unlockCanvasAndPost(clearCanvas);      // 提交画布
        }
//        surfaceTexture = surface;
        if (isDebug) {
            L.d(TAG, "surfaceCreated: animBitmapQueue.size() = " + animBitmapQueue.size());
        }
        canDrawBtmOnCanvas = true;
        if (animBitmapQueue != null) {
            animBitmapQueue.clear();
        } else {
            animBitmapQueue = new ConcurrentLinkedQueue<>();
        }

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        canDrawBtmOnCanvas = false;
        releaseAnim();
        try {
            Thread.sleep(mFrameSpaceTime);

            if (mThread != null) {
                L.d(TAG, "surfaceDestroyed: Thread " + mThread.getState());
            } else {
                L.d(TAG, "surfaceDestroyed: Thread = null");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public interface AnimationCallback {
        void animationComplete();
    }

    public AnimationSurfaceView(Context context) {
        this(context, null);
    }

    public AnimationSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimationSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSurfaceTextureListener(this);
        setAlpha(0.99f);

        maxSize = (int) (Runtime.getRuntime().maxMemory()) / 20;

        screenWidth = ScreenUtils.getScreenWidth(context);
        screenHeight = ScreenUtils.getScreenHeight(context);
    }

    public void startAnimation(IHeFanMessage message, final AnimationCallback callback) {
        this.animationCallback = callback;
        this.message = message;

        if (message == null || isStop) {
            return;
        }
        String id = message.getGiftId();

        if (message.getMessageType() == HefanIMConstants.HFMessageType.HORIZONTAL_GIFT) {
            //横屏大礼物预留
            switch (id) {
                case "164":
                    resIds = PresentConstans.fingerlover;
                    isResource = true;
                    break;
                case "163":
                    resIds = PresentConstans.kiss;
                    isResource = true;
                    break;
                default:
                    isResource = false;
                    break;
            }
        } else {
            //竖屏大礼物预留
            isResource = false;
        }
        if (isResource) {
            if (resIds == null || resIds.length == 0) {
                animationComplete();
                return;
            }
            bmpCount = resIds.length;
        } else {
            if (message.getMessageType() == HefanIMConstants.HFMessageType.HORIZONTAL_GIFT) {
                //横屏大礼物
                giftPath = getContext().getFilesDir().getAbsolutePath() + File.separator + "biggift" + File.separator + id + "_hs" + File.separator;
            } else {
                //竖屏大礼物
                giftPath = getContext().getFilesDir().getAbsolutePath() + File.separator + "biggift" + File.separator + id + File.separator;
            }
            bmpCount = ZIP.getDirFileCount(giftPath);

            if (bmpCount == 0) {
                animationComplete();
                return;
            }
        }

        readFile2Bitmap = true;
        canDrawBtmOnCanvas = true;

        mReadBitmapThread = new Thread(readBitmapRunnable);
        mReadBitmapThread.start();
    }

    Runnable readBitmapRunnable = new Runnable() {
        @Override
        public void run() {
            while (readFile2Bitmap) {
//                Log.d(TAG, "run: bmpCount="+bmpCount+" loadIndex="+loadIndex);
//                Log.d(TAG, "run: animBitmapQueue.size()="+animBitmapQueue.size()+" curSize="+curSize+" maxSize="+maxSize);
                if (animBitmapQueue.size() < 20 && curSize < maxSize) {
                    try {
                        lastTime = System.currentTimeMillis();
                        Bitmap tempBtm;

                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = false;
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        if (animBitmapQueue.size() < 3) {
                            opts.inSampleSize = calculateInSampleSize(opts, screenWidth, screenHeight, gapTime);
                        } else {
                            opts.inSampleSize = 1;
                        }
                        if (isResource) {
                            tempBtm = BitmapFactory.decodeResource(getResources(), resIds[loadIndex], opts);
                        } else {
                            tempBtm = fileToBitmap(giftPath + (loadIndex + 1) + ".png", opts);
                        }
                        gapTime = System.currentTimeMillis() - lastTime;
                        lastTime = System.currentTimeMillis();
                        curSize += tempBtm.getByteCount();
                        animBitmapQueue.add(tempBtm);
//                        if (isDebug)
//                            Log.d(TAG, "onNext load time = "+gapTime+"  loadIndex = "+loadIndex+"[bitmap cache = "+animBitmapQueue.size()+"]");
                        loadIndex++;
                        if (loadIndex >= bmpCount) {
                            break;
                        }
                    } catch (OutOfMemoryError e) {
                        break;
                    } catch (Exception e) {
                        break;
                    }
                } else {
                    synchronized (AnimationSurfaceView.class) {
//                        Log.d(TAG, "readBitmapRunnable: drawPermission="+drawPermission);
                        if (drawPermission && canDrawBtmOnCanvas && readFile2Bitmap) {
                            startFrams();
                        }
                    }
                }
            }
            loadComplete = true;
        }
    };

    /**
     * 动画结束回调
     */
    private void animationComplete() {
        bmpCount = 0;
        loadIndex = 0;
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (animationCallback != null) {
                    animationCallback.animationComplete();
                }
            }
        });
    }

    /**
     * @param path
     * @desc 文件转Bitmap
     */
    private Bitmap fileToBitmap(String path, BitmapFactory.Options opts) {
        return BitmapFactory.decodeFile(path, opts);
    }

    private synchronized void startFrams() {
        if (mThread != null && mThread.isAlive()) {
            canDrawBtmOnCanvas = true;
        } else {
            mThread = new Thread(this);
            mThread.start();
        }
        drawPermission = false;
        L.d(TAG, "startFrams: isFirst=" + drawPermission);
    }

//    @Override
//    public void surfaceCreated(SurfaceHolder holder) {
//        if (isDebug)
//            Log.d(TAG, "surfaceCreated: animBitmapQueue.size() = " + animBitmapQueue.size());
//        mIsDraw = true;
//        if (animBitmapQueue != null) {
//            animBitmapQueue.clear();
//        } else {
//            animBitmapQueue = new ConcurrentLinkedQueue<>();
//        }
//
//    }

//    @Override
//    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//
//    }
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder holder) {
//        mIsDraw = false;
//        releaseAnim();
//        try {
//            Thread.sleep(mFrameSpaceTime);
//
//            if (mThread != null) {
//                Log.d(TAG, "surfaceDestroyed: Thread " + mThread.getState());
//            } else {
//                Log.d(TAG, "surfaceDestroyed: Thread = null");
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }

    @Override
    public void run() {
        synchronized (this) {         // 这里加锁为了以后控制这个绘制线程的wait与notify
            while (canDrawBtmOnCanvas && !isStop) {
                try {
                    mDrawTime = System.currentTimeMillis();
                    drawView();
                    mDrawTime = System.currentTimeMillis() - mDrawTime;
                    long sleepTime = mFrameSpaceTime - mDrawTime;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            recycle(mCanRecycleBtm);
            drawPermission = true;
            L.d(TAG, "run: isFirst=" + drawPermission);
            loadComplete = false;
            animationComplete();
        }
    }

    long drawviewTime = 0;
    BitmapDrawable drawable;

    private void drawView() {
        drawviewTime = System.currentTimeMillis() - drawviewTime;
        if (isDebug) {
            L.i(TAG, "drawView: start gapTime = " + drawviewTime);
        }
        drawviewTime = System.currentTimeMillis();
        Bitmap bmp = animBitmapQueue.poll();
        if (bmp == null) {
            if (drawPermission || !readFile2Bitmap) {
                canDrawBtmOnCanvas = false;
            }
            if (isDebug) {
                L.d(TAG, "drawView: bmp == null");
            }
            Canvas clearCanvas = this.lockCanvas();
            // TODO: 2017/8/2 切换上下滑直播间  走catch退出大礼物绘制
            if (clearCanvas != null) {
                clearCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);        // 清除屏幕
                this.unlockCanvasAndPost(clearCanvas);      // 提交画布
            }
            return;
        }
        Canvas mCanvas = this.lockCanvas();      // 锁定画布
        try {
            drawable = new BitmapDrawable(getResources(), bmp);
            drawable.setBounds(0, 0, getWidth(), getHeight());
            mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);        // 清除屏幕

            drawable.draw(mCanvas);
            curSize -= drawable.getBitmap().getByteCount();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            mCurrentIndext++;
            if (mCanvas != null) {
                this.unlockCanvasAndPost(mCanvas);      // 提交画布
            }
            if (loadComplete && animBitmapQueue.size() <= 0) {
                canDrawBtmOnCanvas = false;
                Canvas clearCanvas = this.lockCanvas();
                clearCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);        // 清除屏幕
                if (clearCanvas != null) {
                    this.unlockCanvasAndPost(clearCanvas);      // 提交画布
                }
            }
            recycle(mCanRecycleBtm);  // 这里回收资源非常重要！
            mCanRecycleBtm = drawable.getBitmap();
            if (isDebug) {
                L.i(TAG, "drawView: end");
            }
        }
    }

    private void recycle(Bitmap mBitmap) {
        if (mBitmap != null) {
            mBitmap.recycle();
//            Runtime.getRuntime().gc();
        }
    }

    public synchronized void releaseAnim() {
        bmpCount = 0;
        loadIndex = 0;
        readFile2Bitmap = false;
        canDrawBtmOnCanvas = false;
        drawPermission = true;
        L.d(TAG, "releaseAnim: drawPermission=" + drawPermission);
        loadComplete = false;
        while (animBitmapQueue.size() > 0) {
            recycle(animBitmapQueue.poll());
        }
        curSize = 0;
        animBitmapQueue = new ConcurrentLinkedQueue<>();
    }

    /**
     * 暂停
     */
    public void stop() {
        isStop = true;
        releaseAnim();
        Canvas clearCanvas = this.lockCanvas();
        // TODO: 2017/8/2 切换上下滑直播间  走catch退出大礼物绘制
        if (clearCanvas != null) {
            clearCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);        // 清除屏幕
            this.unlockCanvasAndPost(clearCanvas);      // 提交画布
        }
    }

    /**
     * 开始
     */
    public void restart() {
        isStop = false;
    }

    /**
     * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap having a width and
     * height equal to or larger than the requested width and height. This implementation does not
     * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
     * results in a larger bitmap which isn't as useful for caching purposes.
     *
     * @param options   An options object with out* params already populated (run through a decode*
     *                  method with inJustDecodeBounds==true
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @param loadTime  The gift bitmap load time
     * @return The value to be used for inSampleSize
     */
    public int calculateInSampleSize(BitmapFactory.Options options,
                                     int reqWidth, int reqHeight, long loadTime) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (loadTime > mFrameSpaceTime) {
            reqHeight = (int) (reqHeight * ((double) mFrameSpaceTime / (double) loadTime));
            reqWidth = (int) (reqWidth * ((double) mFrameSpaceTime / (double) loadTime));
        }
        if (isDebug) {
            L.d(TAG, "calculateInSampleSize: reqHeight = " + reqHeight + " and reqWidth = " + reqWidth);
        }

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = (int) Math.ceil((float) height / (float) reqHeight);
            } else {
                inSampleSize = (int) Math.ceil((float) width / (float) reqWidth);
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger
            // inSampleSize).

            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down
            // further.
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        if (isDebug) {
            L.d(TAG, "calculateInSampleSize: inSampleSize = " + inSampleSize);
        }
        return inSampleSize;
    }
}
