/*
* Copyright (C) 2015 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.mms.ui.next;

import com.android.mms.ui.ConversationList;

/**
 * DeleteInfo
 * <pre>
 *
 * </pre>
 */
/* package */ class DeleteInfo extends com.android.mms.ui.ConversationList.DeleteInfo {

    public DeleteInfo(MessageDeleteTypes deleteType) {
        super(ConversationList.MessageDeleteTypes.valueOf(deleteType.name()));
    }

    public DeleteInfo(MessageDeleteTypes deleteType, int deleteCount) {
        super(ConversationList.MessageDeleteTypes.valueOf(deleteType.name()), deleteCount);
    }

    public enum MessageDeleteTypes {
        ALL,
        SMS,
        MMS
    }


}
