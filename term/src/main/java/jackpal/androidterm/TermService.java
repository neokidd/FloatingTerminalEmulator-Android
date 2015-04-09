/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm;

import android.app.Service;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.IBinder;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.util.Log;
import android.app.Notification;
import android.app.PendingIntent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.EmulatorView;

import jackpal.androidterm.compat.ServiceForegroundCompat;
import jackpal.androidterm.util.SessionList;

public class TermService extends Service implements TermSession.FinishCallback
{
    /* Parallels the value of START_STICKY on API Level >= 5 */
    private static final int COMPAT_START_STICKY = 1;

    private static final int RUNNING_NOTIFICATION = 1;
    private ServiceForegroundCompat compat;

    private SessionList mTermSessions;

    private WindowManager.LayoutParams mWmParams;
    private WindowManager mWindowManager;
    private LinearLayout mFloatLayout;
    private String TAG = "TermService";
    private EmulatorView mFloatTermView;
    private Button mRestoreButton;
    private Button mDockingButton;
    private ImageButton mRestoreImageButton;
    private ImageButton mDockingImageButton;

//    private WindowManager.LayoutParams.gravity initGravity

    public class TSBinder extends Binder {
        TermService getService() {
            Log.i("TermService", "Activity binding to service");
            return TermService.this;
        }
    }
    private final IBinder mTSBinder = new TSBinder();

    @Override
    public void onStart(Intent intent, int flags) {
    }

    /* This should be @Override if building with API Level >=5 */
    public int onStartCommand(Intent intent, int flags, int startId) {
        return COMPAT_START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i("TermService", "Activity called onBind()");
        return mTSBinder;
    }

    @Override
    public void onCreate() {
        compat = new ServiceForegroundCompat(this);
        mTermSessions = new SessionList();

        /* Put the service in the foreground. */
        Notification notification = new Notification(R.drawable.ic_stat_service_notification_icon, getText(R.string.service_notify_text), System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        Intent notifyIntent = new Intent(this, Term.class);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.application_terminal), getText(R.string.service_notify_text), pendingIntent);
        compat.startForeground(RUNNING_NOTIFICATION, notification);

        Log.d(TermDebug.LOG_TAG, "TermService started");
        return;
    }

    @Override
    public void onDestroy() {
        compat.stopForeground(true);
        for (TermSession session : mTermSessions) {
            /* Don't automatically remove from list of sessions -- we clear the
             * list below anyway and we could trigger
             * ConcurrentModificationException if we do */
            session.setFinishCallback(null);
            session.finish();
        }
        mTermSessions.clear();
        return;
    }

    public SessionList getSessions() {
        return mTermSessions;
    }

    public void createFloatView() {
        mWmParams = new WindowManager.LayoutParams();
        //获取WindowManagerImpl.CompatModeWrapper
        mWindowManager = (WindowManager) getApplication().getSystemService(getApplication().WINDOW_SERVICE);
        //设置window type
        mWmParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        //设置图片格式，效果为背景透明
//        mWmParams.format = PixelFormat.RGBA_8888;
        mWmParams.format = PixelFormat.TRANSLUCENT;
        mWmParams.alpha = 0.8f;
        //设置浮动窗口不可聚焦（实现操作除浮动窗口外的其他可见窗口的操作）
        mWmParams.flags =
//          LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
//          LayoutParams.FLAG_NOT_TOUCHABLE
        ;

        //调整悬浮窗显示的停靠位置为左侧置顶
        mWmParams.gravity = Gravity.LEFT | Gravity.TOP;

        // 以屏幕左上角为原点，设置x、y初始值
        mWmParams.x = 0;
        mWmParams.y = 0;

        /*// 设置悬浮窗口长宽数据
        mWmParams.width = 200;
        mWmParams.height = 80;*/

        //设置悬浮窗口长宽数据
//        mWmParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
//        mWmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

        //设置悬浮窗口长宽数据
        mWmParams.width =  mWindowManager.getDefaultDisplay().getWidth();
        mWmParams.height = mWindowManager.getDefaultDisplay().getHeight() / 2;

        LayoutInflater inflater = LayoutInflater.from(getApplication());
        //获取浮动窗口视图所在布局
        mFloatLayout = (LinearLayout) inflater.inflate(R.layout.float_layout, null);
        //添加mFloatLayout
        mWindowManager.addView(mFloatLayout, mWmParams);

        Log.i(TAG, "mFloatLayout-->left" + mFloatLayout.getLeft());
        Log.i(TAG, "mFloatLayout-->right" + mFloatLayout.getRight());
        Log.i(TAG, "mFloatLayout-->top" + mFloatLayout.getTop());
        Log.i(TAG, "mFloatLayout-->bottom" + mFloatLayout.getBottom());

        //浮动Terminal
        mFloatTermView = (EmulatorView) mFloatLayout.findViewById(R.id.term_view);
        if (!getSessions().isEmpty()) {
            mFloatTermView.attachSession(getSessions().get(0));
            DisplayMetrics metrics = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
            mFloatTermView.setDensity(metrics);
        }

        mFloatLayout.measure(View.MeasureSpec.makeMeasureSpec(0,
                View.MeasureSpec.UNSPECIFIED), View.MeasureSpec
                .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        Log.i(TAG, "Width/2--->" + mFloatTermView.getMeasuredWidth() / 2);
        Log.i(TAG, "Height/2--->" + mFloatTermView.getMeasuredHeight() / 2);

        mRestoreButton = (Button) mFloatLayout.findViewById(R.id.restore_button);
        mRestoreButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
//                closeFloatWindow();
                if (mWindowManager != null && mFloatLayout != null) {
                    mWindowManager.removeView(mFloatLayout);
                }
                startActivity(new Intent(getApplication(), Term.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });

        mDockingButton = (Button) mFloatLayout.findViewById(R.id.docking_button);
        mDockingButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((mWmParams.gravity & Gravity.TOP) == Gravity.TOP) {
                    mWmParams.gravity &= ~Gravity.TOP;
                    mWmParams.gravity |= Gravity.BOTTOM;
                    mDockingButton.setText("DOCKING TOP");
                } else if ((mWmParams.gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                    mWmParams.gravity &= ~Gravity.BOTTOM;
                    mWmParams.gravity |= Gravity.TOP;
                    mDockingButton.setText("DOCKING BOTTOM");
                }
                mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
            }
        });


        mRestoreImageButton = (ImageButton) mFloatLayout.findViewById(R.id.restore_imagebutton);
        mRestoreImageButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
//                closeFloatWindow();
                if (mWindowManager != null && mFloatLayout != null) {
                    mWindowManager.removeView(mFloatLayout);
                }
                startActivity(new Intent(getApplication(), Term.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });

        mDockingImageButton = (ImageButton) mFloatLayout.findViewById(R.id.docking_imagebutton);
        mDockingImageButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((mWmParams.gravity & Gravity.TOP) == Gravity.TOP) {
                    mWmParams.gravity &= ~Gravity.TOP;
                    mWmParams.gravity |= Gravity.BOTTOM;
                    mDockingImageButton.setImageResource(R.drawable.expander_ic_maximized);
                } else if ((mWmParams.gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                    mWmParams.gravity &= ~Gravity.BOTTOM;
                    mWmParams.gravity |= Gravity.TOP;
                    mDockingImageButton.setImageResource(R.drawable.expander_ic_minimized);
                }
                mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
            }
        });

//        //设置监听浮动窗口的触摸移动
//        mFloatTermView.setOnTouchListener(new View.OnTouchListener() {
//
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                // TODO Auto-generated method stub
//                //getRawX是触摸位置相对于屏幕的坐标，getX是相对于按钮的坐标
//                mWmParams.x = (int) event.getRawX() - mFloatTermView.getMeasuredWidth() / 2;
//                //Log.i(TAG, "Width/2--->" + mFloatView.getMeasuredWidth()/2);
//                Log.i(TAG, "RawX" + event.getRawX());
//                Log.i(TAG, "X" + event.getX());
//                //25为状态栏的高度
//                mWmParams.y = (int) event.getRawY() - mFloatTermView.getMeasuredHeight() / 2 - 25;
//                // Log.i(TAG, "Width/2--->" + mFloatView.getMeasuredHeight()/2);
//                Log.i(TAG, "RawY" + event.getRawY());
//                Log.i(TAG, "Y" + event.getY());
//                //刷新
//                mWindowManager.updateViewLayout(mFloatLayout, mWmParams);
//                return false;
//            }
//        });
//
//        mFloatTermView.setOnClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                // TODO Auto-generated method stub
//                Toast.makeText(TermService.this, "onClick", Toast.LENGTH_SHORT).show();
//            }
//        });
    }


    public void onSessionFinish(TermSession session) {
        mTermSessions.remove(session);
    }
}
