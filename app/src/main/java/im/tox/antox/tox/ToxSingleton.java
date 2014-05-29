package im.tox.antox.tox;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.concurrent.Future;

import im.tox.antox.data.AntoxDB;
import im.tox.antox.utils.AntoxFriend;
import im.tox.antox.utils.AntoxFriendList;
import im.tox.antox.utils.Constants;
import im.tox.antox.utils.Friend;
import im.tox.antox.utils.FriendInfo;
import im.tox.antox.utils.FriendRequest;
import im.tox.antox.utils.LeftPaneItem;
import im.tox.antox.utils.Message;
import im.tox.antox.utils.UserDetails;
import im.tox.jtoxcore.JTox;
import im.tox.jtoxcore.ToxException;
import im.tox.jtoxcore.ToxUserStatus;
import im.tox.jtoxcore.callbacks.CallbackHandler;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func3;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subscriptions.Subscriptions;

import static rx.Observable.combineLatest;

public class ToxSingleton {

    private static final String TAG = "im.tox.antox.tox.ToxSingleton";
    public JTox jTox;
    private AntoxFriendList antoxFriendList;
    public CallbackHandler callbackHandler;
    public ArrayList<FriendRequest> friend_requests = new ArrayList<FriendRequest>();
    public boolean toxStarted = false;
    public AntoxFriendList friendsList;
    public String activeFriendRequestKey = null;
    public String activeFriendKey = null;
    public boolean rightPaneActive = false;
    public boolean leftPaneActive = false;
    public NotificationManager mNotificationManager;
    public ToxDataFile dataFile;
    public File qrFile;
    public BehaviorSubject<ArrayList<Friend>> friendListSubject;
    public BehaviorSubject<HashMap> lastMessagesSubject;
    public BehaviorSubject<HashMap> unreadCountsSubject;
    public rx.Observable friendInfoListSubject;

    public void initSubjects(Context ctx){
        friendListSubject = BehaviorSubject.create(new ArrayList<Friend>());
        friendListSubject.subscribeOn(Schedulers.io());
        lastMessagesSubject = BehaviorSubject.create(new HashMap());
        lastMessagesSubject.subscribeOn(Schedulers.io());
        unreadCountsSubject = BehaviorSubject.create(new HashMap());
        unreadCountsSubject.subscribeOn(Schedulers.io());
        friendInfoListSubject = combineLatest(friendListSubject, lastMessagesSubject, unreadCountsSubject, new Func3<ArrayList<Friend>, HashMap, HashMap, ArrayList<FriendInfo>>() {
            @Override
            public ArrayList<FriendInfo> call(ArrayList<Friend> fl, HashMap lm, HashMap uc) {
                ArrayList<FriendInfo> fi = new ArrayList<FriendInfo>();
                for (Friend f : fl) {
                    String lastMessage;
                    int unreadCount;
                    if (lm.containsKey(f.friendKey)) {
                        lastMessage = (String) lm.get(f.friendKey);
                    } else {
                        lastMessage = "";
                    }
                    if (uc.containsKey(f.friendKey)) {
                        unreadCount = (Integer) uc.get(f.friendKey);
                    } else {
                        unreadCount = 0;
                    }
                    fi.add(new FriendInfo(f.icon, f.friendName, f.friendStatus, f.personalNote, f.friendKey, f.friendGroup, lastMessage, unreadCount));
                }
                return fi;
            }
        });
    };

    public void updateFriendsList(Context ctx) {
        try {
            AntoxDB antoxDB = new AntoxDB(ctx);

            ArrayList<Friend> friendList = antoxDB.getFriendList(Constants.OPTION_ALL_FRIENDS);

            antoxDB.close();

            friendListSubject.onNext(friendList);
        } catch (Exception e) {
            friendListSubject.onError(e);
        }
    }

    public void updateMessages(Context ctx) {
        updateLastMessageMap(ctx);
        updateUnreadCountMap(ctx);
    }

    public void updateLastMessageMap(Context ctx) {
        try {
            AntoxDB antoxDB = new AntoxDB(ctx);

            HashMap map = antoxDB.getLastMessages();

            antoxDB.close();

            lastMessagesSubject.onNext(map);
        } catch (Exception e) {
            lastMessagesSubject.onError(e);
        }
    }
    public void updateUnreadCountMap(Context ctx) {
        try {
            AntoxDB antoxDB = new AntoxDB(ctx);

            HashMap map = antoxDB.getUnreadCounts();

            antoxDB.close();

            unreadCountsSubject.onNext(map);
        } catch (Exception e) {
            unreadCountsSubject.onError(e);
        }
    }
    private static volatile ToxSingleton instance = null;

    private ToxSingleton() {
    }

    public void initTox(Context ctx) {
        friendsList = new AntoxFriendList();
        toxStarted = true;
        antoxFriendList = new AntoxFriendList();
        callbackHandler = new CallbackHandler(antoxFriendList);

        try {
            qrFile = ctx.getFileStreamPath("userkey_qr.png");
            dataFile = new ToxDataFile(ctx);

            /* Choose appropriate constructor depending on if data file exists */
            if(!dataFile.doesFileExist()) {
                jTox = new JTox(antoxFriendList, callbackHandler);
                
            } else {
                jTox = new JTox(dataFile.loadFile(), antoxFriendList, callbackHandler);

            }

            if(UserDetails.username == null)
                UserDetails.username = "antoxUser";
            jTox.setName(UserDetails.username);

            if(UserDetails.note == null)
                UserDetails.note = "using antox";
            jTox.setStatusMessage(UserDetails.note);

            if(UserDetails.status == null)
                UserDetails.status = ToxUserStatus.TOX_USERSTATUS_NONE;
            jTox.setUserStatus(UserDetails.status);

            /* Save data file */
            dataFile.saveFile(jTox.save());

        } catch (ToxException e) {
            e.printStackTrace();
            Log.d(TAG, e.getError().toString());
        }
    }

    public static ToxSingleton getInstance() {
        /* Double-checked locking */
        if(instance == null) {
            synchronized (ToxSingleton.class) {
                if(instance == null) {
                    instance = new ToxSingleton();
                }
            }
        }

        return instance;
    }
}
