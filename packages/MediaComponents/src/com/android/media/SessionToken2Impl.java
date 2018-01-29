/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.media;

import static android.media.SessionToken2.TYPE_SESSION;
import static android.media.SessionToken2.TYPE_SESSION_SERVICE;
import static android.media.SessionToken2.TYPE_LIBRARY_SERVICE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.media.MediaLibraryService2;
import android.media.MediaSessionService2;
import android.media.SessionToken2;
import android.media.SessionToken2.TokenType;
import android.media.update.SessionToken2Provider;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;

public class SessionToken2Impl implements SessionToken2Provider {
    private static final String KEY_UID = "android.media.token.uid";
    private static final String KEY_TYPE = "android.media.token.type";
    private static final String KEY_PACKAGE_NAME = "android.media.token.package_name";
    private static final String KEY_SERVICE_NAME = "android.media.token.service_name";
    private static final String KEY_ID = "android.media.token.id";
    private static final String KEY_SESSION_BINDER = "android.media.token.session_binder";

    private final SessionToken2 mInstance;
    private final int mUid;
    private final @TokenType int mType;
    private final String mPackageName;
    private final String mServiceName;
    private final String mId;
    private final IMediaSession2 mSessionBinder;

    public SessionToken2Impl(Context context, SessionToken2 instance, int uid, int type,
            String packageName, String serviceName, String id, IMediaSession2 sessionBinder) {
        // TODO(jaewan): Add sanity check
        mInstance = instance;
        if (uid < 0) {
            PackageManager manager = context.getPackageManager();
            try {
                uid = manager.getPackageUid(packageName, 0);
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException("Invalid uid=" + uid);
            }
        }
        mUid = uid;
        mType = type;
        mPackageName = packageName;
        mServiceName = serviceName;
        if (id == null && !TextUtils.isEmpty(mServiceName)) {
            // Will be called for an app with no
            PackageManager manager = context.getPackageManager();
            String action;
            switch (type) {
                case TYPE_SESSION_SERVICE:
                    action = MediaSessionService2.SERVICE_INTERFACE;
                    break;
                case TYPE_LIBRARY_SERVICE:
                    action = MediaLibraryService2.SERVICE_INTERFACE;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid type");
            }
            Intent serviceIntent = new Intent(action);
            serviceIntent.setClassName(packageName, serviceName);
            id = getSessionId(manager.resolveService(serviceIntent,
                    PackageManager.GET_META_DATA));
            if (id == null) {
                throw new IllegalArgumentException("service " + serviceName + " doesn't implement"
                        + serviceIntent.getAction());
            }
        } else if (id == null) {
            throw new IllegalArgumentException("ID shouldn't be null");
        }
        mId = id;
        mSessionBinder = sessionBinder;
    }

    public static String getSessionId(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
            return null;
        } else if (resolveInfo.serviceInfo.metaData == null) {
            return "";
        } else {
            return resolveInfo.serviceInfo.metaData.getString(
                    MediaSessionService2.SERVICE_META_DATA, "");
        }
    }

    @Override
    public String getPackageName_impl() {
        return mPackageName;
    }

    @Override
    public int getUid_impl() {
        return mUid;
    }

    @Override
    public String getId_imp() {
        return mId;
    }

    @Override
    public int getType_impl() {
        return mType;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public IMediaSession2 getSessionBinder() {
        return mSessionBinder;
    }

    public static SessionToken2 fromBundle(Context context, Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        final int uid = bundle.getInt(KEY_UID);
        final @TokenType int type = bundle.getInt(KEY_TYPE, -1);
        final String packageName = bundle.getString(KEY_PACKAGE_NAME);
        final String serviceName = bundle.getString(KEY_SERVICE_NAME);
        final String id = bundle.getString(KEY_ID);
        final IBinder sessionBinder = bundle.getBinder(KEY_SESSION_BINDER);

        // Sanity check.
        switch (type) {
            case TYPE_SESSION:
                if (sessionBinder == null) {
                    throw new IllegalArgumentException("Unexpected sessionBinder for session,"
                            + " binder=" + sessionBinder);
                }
                break;
            case TYPE_SESSION_SERVICE:
            case TYPE_LIBRARY_SERVICE:
                if (TextUtils.isEmpty(serviceName)) {
                    throw new IllegalArgumentException("Session service needs service name");
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid type");
        }
        if (TextUtils.isEmpty(packageName) || id == null) {
            throw new IllegalArgumentException("Package name nor ID cannot be null.");
        }
        // TODO(jaewan): Revisit here when we add connection callback to the session for individual
        //               controller's permission check. With it, sessionBinder should be available
        //               if and only if for session, not session service.
        return new SessionToken2(context, uid, type, packageName, serviceName, id,
                sessionBinder != null ? IMediaSession2.Stub.asInterface(sessionBinder) : null);
    }

    @Override
    public Bundle toBundle_impl() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_UID, mUid);
        bundle.putString(KEY_PACKAGE_NAME, mPackageName);
        bundle.putString(KEY_SERVICE_NAME, mServiceName);
        bundle.putString(KEY_ID, mId);
        bundle.putInt(KEY_TYPE, mType);
        bundle.putBinder(KEY_SESSION_BINDER,
                mSessionBinder != null ? mSessionBinder.asBinder() : null);
        return bundle;
    }

    @Override
    public int hashCode_impl() {
        final int prime = 31;
        return mType
                + prime * (mUid
                + prime * (mPackageName.hashCode()
                + prime * (mId.hashCode()
                + prime * ((mServiceName != null ? mServiceName.hashCode() : 0)
                + prime * (mSessionBinder != null ? mSessionBinder.asBinder().hashCode() : 0)))));
    }

    @Override
    public boolean equals_impl(Object obj) {
        if (!(obj instanceof SessionToken2)) {
            return false;
        }
        SessionToken2Impl other = from((SessionToken2) obj);
        if (mUid != other.mUid
                || !TextUtils.equals(mPackageName, other.mPackageName)
                || !TextUtils.equals(mServiceName, other.mServiceName)
                || !TextUtils.equals(mId, other.mId)
                || mType != other.mType) {
            return false;
        }
        if (mSessionBinder == other.mSessionBinder) {
            return true;
        } else if (mSessionBinder == null || other.mSessionBinder == null) {
            return false;
        }
        return mSessionBinder.asBinder().equals(other.mSessionBinder.asBinder());
    }

    @Override
    public String toString_impl() {
        return "SessionToken {pkg=" + mPackageName + " id=" + mId + " type=" + mType
                + " service=" + mServiceName + " binder=" + mSessionBinder + "}";
    }

    public static SessionToken2Impl from(SessionToken2 token) {
        return ((SessionToken2Impl) token.getProvider());
    }
}