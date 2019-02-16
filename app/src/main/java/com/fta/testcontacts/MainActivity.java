package com.fta.testcontacts;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.StatFs;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fta.testcontacts.utils.FileUtils;
import com.fta.testcontacts.utils.ImportRequest;
import com.fta.testcontacts.utils.StorageUtils;
import com.fta.testcontacts.utils.VCardCacheThread;
import com.fta.testcontacts.utils.VCardImportExportListener;
import com.fta.testcontacts.utils.VCardService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnClickListener {

    private static final String TAG = "main_ac";

    private Button btn;// 导入按钮
    private TextView show;// 显示结果的文本框
    private Thread addThread;// 增加联系人线程
    private static final int ADD_FAIL = 0;// 导入失败标识
    private static final int ADD_SUCCESS = 1;// 导入成功标识
    private static int successCount = 0;// 导入成功的计数
    private static int failCount = 0;// 导入失败的计数
    // 默认文件路劲，实际情况应作相应修改或从界面输入或浏览选择
//    private static final String PATH = Environment
//            .getExternalStorageDirectory() + "/00001.vcf";

    private static final String PATH = "/storage/4B92-1317/00001.vcf";

//    private static final String PATH1 = Environment
//            .getExternalStorageDirectory() + "/00005.vcf";

    private ImportRequestConnection mConnection;

    private VCardImportExportListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

        mConnection = new ImportRequestConnection();
        Log.i(TAG, "Bind to VCardService.");
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this, VCardService.class);
        startService(intent);
        bindService(new Intent(this, VCardService.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 初始化组件
     */
    private void init() {
        btn = (Button) findViewById(R.id.main_btn);
        btn.setOnClickListener(this);
        show = (TextView) findViewById(R.id.main_tv);
    }

    @Override
    public void onClick(View v) {
        if (v == btn) {
//            addContact();


//            Intent intent = new Intent(Intent.ACTION_VIEW);
//            Uri data;
//            String type = "text/x-vcard";
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
//                data = Uri.fromFile(new File(PATH));
//            } else {
//                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//                String authority = this.getPackageName() + ".fileprovider";
//                data = FileProvider.getUriForFile(this, authority, new File(PATH));
//            }
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.setDataAndType(data, type);
//            startActivity(intent);

            // TODO: 2019/2/15
            VCardCacheThread vCardCacheThread = new VCardCacheThread(mConnection, v.getContext()
                    , new Uri[]{Uri.fromFile(new File(PATH))}, new String[]{"abc"});
            vCardCacheThread.start();
        }
    }


    public void sdcardexist(View view) {
        boolean b = FileUtils.sdcardExist();
        Log.i(TAG, "MainActivity-sdcardexist: " + b);
        if (b) {
            String absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath();
            Log.i(TAG, "MainActivity-sdcardexist: " + absolutePath);
            StatFs statFs = new StatFs(absolutePath);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                long availableBlocksLong = statFs.getAvailableBlocksLong();
                Log.i(TAG, "MainActivity-sdcardexist: " + availableBlocksLong);
            }

        }

//        boolean sdMounted = FileUtils.isSDMounted(this);
//        Log.i(TAG, "MainActivity-sdcardexist: " + sdMounted);

        ArrayList<StorageUtils.Volume> volumes = StorageUtils.getVolume(this);
        if (volumes != null && volumes.size() > 0) {
            for (StorageUtils.Volume volume : volumes) {
                Log.i(TAG, "MainActivity-sdcardexist: 存储卡信息：" + volume.toString());
                if (volume.isRemovable()) {
                    String path = volume.getPath();
                    File file = new File(path);
                    String[] strings = file.list();
                    Log.i(TAG, "MainActivity-sdcardexist: 外部存储卡：" + (strings != null ? strings.length : 0));
                }
            }
        } else {
            Log.i(TAG, "MainActivity-sdcardexist: is null");
        }
    }

    public class ImportRequestConnection implements ServiceConnection {
        private VCardService mService;

        public void sendImportRequest(final List<ImportRequest> requests) {
            Log.i(TAG, "Send an import request");
            mService.handleImportRequest(requests, mListener);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "ImportRequestConnection-onServiceConnected: ");
            mService = ((VCardService.MyBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Disconnected from VCardService");
        }
    }

    /**
     * 导入联系人入口
     */
    private void addContact() {
        if (!new File(PATH).exists()) {
            Toast.makeText(this, "文件不存在!", Toast.LENGTH_SHORT).show();
            show.setText("文件不存在!");
            return;
        }
        if (addThread != null) {
            addThread.interrupt();
            addThread = null;
        }
        addThread = new Thread(new AddRunnable(this, PATH));
        createDialog(this, "警告", "确保你是第一次导入，重复导入会创建新的联系人，请慎用！");
    }

    /**
     * 创建提示对话框
     *
     * @param context
     * @param title
     * @param message
     */
    private void createDialog(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                startAddContact();
            }
        });
        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    /**
     * 开启导入线程
     */
    private void startAddContact() {
        setAddWidgetEnabled(false);
        show.setText("正在导入联系人...");
        if (addThread != null) {
            addThread.start();
        }
    }

    class AddRunnable implements Runnable {
        private Context context;
        private String path;

        public AddRunnable(Context context, String path) {
            this.path = path;
            this.context = context;
        }

        @Override
        public void run() {
            boolean result = importContact(context, path);
            if (result) {
                handler.sendEmptyMessage(ADD_SUCCESS);
            } else {
                handler.sendEmptyMessage(ADD_FAIL);
            }
        }
    }

    /**
     * 处理UI相关的handler
     */
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ADD_FAIL:
                    show.setText("导入联系人失败");
                    setAddWidgetEnabled(true);
                    break;
                case ADD_SUCCESS:
                    show.setText(String.format("导入联系人成功 %d 条，失败 %d 条",
                            successCount, failCount));
                    setAddWidgetEnabled(true);
                    break;
            }
        }
    };

    /**
     * 设置导入组件的可用性
     *
     * @param enabled
     */
    private void setAddWidgetEnabled(boolean enabled) {
        btn.setEnabled(enabled);
        if (!enabled) {
            show.setText("");
        }
    }

    /**
     * 导入联系人
     *
     * @param context
     * @param path
     * @return
     */
    private boolean importContact(Context context, String path) {
//        successCount = 0;
//        failCount = 0;
//        try {
//            ArrayList<ContactInfo> list = readFromFile(path);
//            if (list == null) {
//                return false;
//            }
//            for (int i = 0; i < list.size(); i++) {
//                ContactInfo info = list.get(i);
//                if (doAddContact(context, info)) {
//                    successCount++;
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//        return true;


        successCount = 0;
        failCount = 0;
        try {
            List<EntityContact> list = readFromFile(path);
            if (list == null) {
                return false;
            }
            for (int i = 0; i < list.size(); i++) {
                EntityContact info = list.get(i);
                Log.i(TAG, "MainActivity-importContact: 第" + i + "个信息=" + info.toString());
                if (doAddContact1(context, info)) {
                    successCount++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 读取联系人并封装成ContactInfo对象集合
     *
     * @param path
     * @return contactsList
     */
    private List<EntityContact> readFromFile(String path) {
//        ArrayList<String> strsList = doReadFile(path);
//        if (strsList == null) {
//            return null;
//        }
//        ArrayList<ContactInfo> contactsList = handleReadStrs(strsList);
//        return contactsList;
        List<EntityContact> entityContacts = doReadFile1(path);
        if (entityContacts != null && entityContacts.size() > 0) {
            Log.i(TAG, "MainActivity-readFromFile: is ok");
            return entityContacts;
        } else {
            Log.i(TAG, "MainActivity-readFromFile: is null");
            return null;
        }
    }

    /**
     * 将读出来的内容封装成ContactInfo对象集合
     *
     * @param strsList
     * @return
     */
    private ArrayList<ContactInfo> handleReadStrs(ArrayList<String> strsList) {
        ArrayList<ContactInfo> contactsList = new ArrayList<ContactInfo>();
        for (int i = 0; i < strsList.size(); i++) {
            String info = strsList.get(i);
            String[] infos = info.split("\\s{2,}");
            String displayName = null;
            String mobileNum = null;
            String homeNum = null;
            switch (infos.length) {
                case 0:
                    continue;
                case 1:
                    displayName = infos[0];
                    break;
                case 2:
                    displayName = infos[0];
                    if (infos[1].length() >= 11) {
                        mobileNum = infos[1];
                    } else {
                        homeNum = infos[1];
                    }
                    break;
                default:
                    // length >= 3
                    displayName = infos[0];
                    mobileNum = infos[1];
                    homeNum = infos[2];
            }
            if (displayName == null || "".equals(displayName)) {
                failCount++;
                continue;
            }
            contactsList.add(new ContactInfo(displayName, mobileNum, homeNum));
        }
        return contactsList;
    }

    /**
     * 读取文件内容
     *
     * @param path
     * @return
     */
    private ArrayList<String> doReadFile(String path) {
        FileInputStream in = null;
        ArrayList<String> arrayList = new ArrayList<String>();

        try {
            byte[] tempbytes = new byte[1 << 24];
            in = new FileInputStream(path);
            while (in.read(tempbytes) != -1) {
                int length = 0;
                int first = length;
                for (int i = 0; i < tempbytes.length; i++) {
                    if (tempbytes[i] == '\n') {
                        length = i;
                        byte[] nowBytes = new byte[length - first];
                        System.arraycopy(tempbytes, first, nowBytes, 0, length
                                - first);
                        String trim = new String(nowBytes, "utf-8").trim();
                        Log.i(TAG, "MainActivity-doReadFile: 读取的信息：" + trim);
                        arrayList.add(trim);
                        first = i + 1;
                    }
                }

            }

        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e1) {
                    return null;
                }
            }
        }
        return arrayList;
    }

    /**
     * 读取文件内容
     *
     * @param path
     * @return
     */
    private List<EntityContact> doReadFile1(String path) {
        //fileExtend为选中文件的地址后缀。
        BufferedReader reader = null;
        List<EntityContact> contacts = new ArrayList<>();
        if (path.endsWith("vcf")) {
            //处理VCF格式数据。
            EntityContact contact = null;
            try {
                String buffer = null;
                reader = new BufferedReader(new FileReader(path));
                while ((buffer = reader.readLine()) != null) {
                    if (buffer.equals("BEGIN:VCARD")) {
                        //开始标识符。
                        contact = new EntityContact();
                    } else if (buffer.startsWith("N:;")) {
                        //名字。
                        buffer = buffer.substring(buffer.indexOf(";") + 1, buffer.lastIndexOf(";;;"));
                        if (contact != null)
                            contact.setName(buffer);
                    } else if (buffer.startsWith("FN;") && buffer.contains("CHARSET") && buffer.contains("ENCODING")) {
                        //名字
                        buffer = buffer.substring(buffer.indexOf(buffer.lastIndexOf(":")));
                        if (contact != null)
                            contact.setName(buffer);
                    } else if (buffer.startsWith("TEL;CELL")) {
                        //手机号。
                        buffer = buffer.substring(buffer.indexOf(":") + 1);
                        if (contact != null)
                            contact.setMobile_num(buffer);
                    } else if (buffer.startsWith("TEL;WORK;VOICE:")) {
                        //工作号码。
                        buffer = buffer.substring(buffer.indexOf(":") + 1);
                        if (contact != null)
                            contact.setOffice_num(buffer);
                    } else if (buffer.startsWith("TEL;HOME;VOICE:")) {
                        //家用号码。
                        buffer = buffer.substring(buffer.indexOf(":") + 1);
                        if (contact != null)
                            contact.setHome_num(buffer);
                    } else if (buffer.startsWith("EMAIL;HOME:")) {
                        buffer = buffer.substring(buffer.indexOf(":") + 1);
                        if (contact != null)
                            contact.setEmail(buffer);
                    } else if (buffer.startsWith("ADR;HOME:")) {
                        buffer = buffer.substring(buffer.indexOf(":") + 1, buffer.lastIndexOf(";;;;;;"));
                        if (contact != null)
                            contact.setHome_address(buffer);
                    } else if (buffer.startsWith("ADR;WORK:")) {
                        buffer = buffer.substring(buffer.indexOf(":") + 1, buffer.lastIndexOf(";;;;;;"));
                        if (contact != null)
                            contact.setOffice_address(buffer);
                    } else if (buffer.startsWith("NOTE;WORK:")) {
                        buffer = buffer.substring(buffer.indexOf(":") + 1);
                        if (contact != null)
                            contact.setExtend(buffer);
                    } else if (buffer.startsWith("PHOTO;")) {
                        //头像数据。
                        buffer = buffer.substring(buffer.indexOf(":") + 1);
                        if (contact != null)
                            contact.setPhoto(buffer);
                    } else if (buffer.equals("END:VCARD")) {
                        //结束标识符。
                        contacts.add(contact);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return contacts;
    }

    /**
     * 向数据库表插入联系人信息
     *
     * @param context
     * @param contactInfo
     * @return
     */
    private boolean doAddContact(Context context, ContactInfo contactInfo) {
        try {
            ContentValues contentValues = new ContentValues();
            Uri uri = context.getContentResolver().insert(
                    RawContacts.CONTENT_URI, contentValues);
            long rowId = ContentUris.parseId(uri);

            String name = contactInfo.getDisplayName();
            String mobileNum = contactInfo.getMobileNum();
            String homeNum = contactInfo.getHomeNum();

            // 插入姓名
            if (name != null) {
                contentValues.clear();
                contentValues.put(Data.RAW_CONTACT_ID, rowId);
                contentValues.put(Data.MIMETYPE,
                        StructuredName.CONTENT_ITEM_TYPE);
                int index = name.length() / 2;
                String displayName = name;
                String givenName = null;
                String familyName = null;
                // 检查是否是英文名称
                if (checkEnglishName(displayName) == false) {
                    givenName = name.substring(index);
                    familyName = name.substring(0, index);
                } else {
                    givenName = familyName = displayName;
                }
                contentValues.put(StructuredName.DISPLAY_NAME, displayName);
                contentValues.put(StructuredName.GIVEN_NAME, givenName);
                contentValues.put(StructuredName.FAMILY_NAME, familyName);
                context.getContentResolver().insert(
                        ContactsContract.Data.CONTENT_URI, contentValues);
            }

            if (mobileNum != null) {
                // 插入手机电话
                contentValues.clear();
                contentValues.put(Data.RAW_CONTACT_ID, rowId);
                contentValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                contentValues.put(Phone.NUMBER, mobileNum);
                contentValues.put(Phone.TYPE, Phone.TYPE_MOBILE);
                context.getContentResolver().insert(
                        ContactsContract.Data.CONTENT_URI, contentValues);
            }

            if (homeNum != null) {
                // 插入家庭号码
                contentValues.clear();
                contentValues.put(Data.RAW_CONTACT_ID, rowId);
                contentValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                contentValues.put(Phone.NUMBER, homeNum);
                contentValues.put(Phone.TYPE, Phone.TYPE_HOME);
                context.getContentResolver().insert(
                        ContactsContract.Data.CONTENT_URI, contentValues);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 向数据库表插入联系人信息
     *
     * @param context
     * @param contactInfo
     * @return
     */
    private boolean doAddContact1(Context context, EntityContact contactInfo) {
        try {
            ContentValues contentValues = new ContentValues();
            Uri uri = context.getContentResolver().insert(
                    RawContacts.CONTENT_URI, contentValues);
            long rowId = ContentUris.parseId(uri);

            String name = contactInfo.getName();
            String mobileNum = contactInfo.getMobile_num();
            String homeNum = contactInfo.getHome_num();

            // 插入姓名
            if (name != null) {
                contentValues.clear();
                contentValues.put(Data.RAW_CONTACT_ID, rowId);
                contentValues.put(Data.MIMETYPE,
                        StructuredName.CONTENT_ITEM_TYPE);
                int index = name.length() / 2;
                String displayName = name;
                String givenName = null;
                String familyName = null;
                // 检查是否是英文名称
                if (checkEnglishName(displayName) == false) {
                    givenName = name.substring(index);
                    familyName = name.substring(0, index);
                } else {
                    givenName = familyName = displayName;
                }
                contentValues.put(StructuredName.DISPLAY_NAME, displayName);
                contentValues.put(StructuredName.GIVEN_NAME, givenName);
                contentValues.put(StructuredName.FAMILY_NAME, familyName);
                context.getContentResolver().insert(
                        ContactsContract.Data.CONTENT_URI, contentValues);
            }

            if (mobileNum != null) {
                // 插入手机电话
                contentValues.clear();
                contentValues.put(Data.RAW_CONTACT_ID, rowId);
                contentValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                contentValues.put(Phone.NUMBER, mobileNum);
                contentValues.put(Phone.TYPE, Phone.TYPE_MOBILE);
                context.getContentResolver().insert(
                        ContactsContract.Data.CONTENT_URI, contentValues);
            }

            if (homeNum != null) {
                // 插入家庭号码
                contentValues.clear();
                contentValues.put(Data.RAW_CONTACT_ID, rowId);
                contentValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                contentValues.put(Phone.NUMBER, homeNum);
                contentValues.put(Phone.TYPE, Phone.TYPE_HOME);
                context.getContentResolver().insert(
                        ContactsContract.Data.CONTENT_URI, contentValues);
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 检查是否是英文名称
     *
     * @param name
     * @return
     */
    private boolean checkEnglishName(String name) {
        char[] nameChars = name.toCharArray();
        for (int i = 0; i < nameChars.length; i++) {
            if ((nameChars[i] >= 'a' && nameChars[i] <= 'z')
                    || (nameChars[i] >= 'A' && nameChars[i] <= 'Z')) {
                continue;
            }
            return false;
        }
        return true;
    }


}
