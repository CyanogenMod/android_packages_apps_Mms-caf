/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.mms.data;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.android.contacts.common.preference.ContactsPreferences;

public class RecipientsListLoader extends AsyncTaskLoader<RecipientsListLoader.Result> {
    private Result mResults;
    private ContactsPreferences mContactsPreferences;

    public static class Result {
        public List<PhoneNumber> phoneNumbers;
        public List<Group> groups;
    }

    public RecipientsListLoader(Context context, ContactsPreferences prefs) {
        super(context);
        mContactsPreferences = prefs;
    }

    @Override
    public Result loadInBackground() {
        final Context context = getContext();
        ArrayList<PhoneNumber> phoneNumbers = PhoneNumber.getPhoneNumbers(context,
                mContactsPreferences);
        if (phoneNumbers == null) {
            return new Result();
        }

        ArrayList<Group> groups = Group.getGroups(context);
        ArrayList<GroupMembership> groupMemberships =
                GroupMembership.getGroupMemberships(context);
        Map<Long, ArrayList<Long>> groupIdWithContactsId = new HashMap<Long, ArrayList<Long>>();

        // Store GID with all its CIDs
        if (groups != null && groupMemberships != null) {
            for (GroupMembership membership : groupMemberships) {
                Long gid = membership.getGroupId();
                Long uid = membership.getContactId();

                if (!groupIdWithContactsId.containsKey(gid)) {
                    groupIdWithContactsId.put(gid, new ArrayList<Long>());
                }

                if (!groupIdWithContactsId.get(gid).contains(uid)) {
                    groupIdWithContactsId.get(gid).add(uid);
                }
            }

            // For each PhoneNumber, find its GID, and add it to correct Group
            for (PhoneNumber phoneNumber : phoneNumbers) {
                long cid = phoneNumber.getContactId();

                for (Map.Entry<Long, ArrayList<Long>> entry : groupIdWithContactsId.entrySet()) {
                    if (!entry.getValue().contains(cid)) {
                        continue;
                    }
                    for (Group group : groups) {
                        if (group.getId() == entry.getKey()) {
                            group.addPhoneNumber(phoneNumber);
                            phoneNumber.addGroup(group);
                        }
                    }
                }
            }

            // Due to filtering there may be groups that have contacts, but no
            // phone numbers. Filter those.
            Iterator<Group> groupIter = groups.iterator();
            while (groupIter.hasNext()) {
                Group group = groupIter.next();
                if (group.getPhoneNumbers().isEmpty()) {
                    groupIter.remove();
                }
            }
        }

        Result result = new Result();
        result.phoneNumbers = phoneNumbers;
        result.groups = groups;

        return result;
    }

    // Called when there is new data to deliver to the client.  The
    // super class will take care of delivering it; the implementation
    // here just adds a little more logic.
    @Override
    public void deliverResult(Result result) {
        mResults = result;

        if (isStarted()) {
            // If the Loader is started, immediately deliver its results.
            super.deliverResult(result);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mResults != null) {
            // If we currently have a result available, deliver it immediately.
            deliverResult(mResults);
        }

        if (takeContentChanged() || mResults == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated if needed.
        mResults = null;
    }
}
