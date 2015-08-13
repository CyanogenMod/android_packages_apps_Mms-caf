# Copyright 2007-2008 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
# Include res dir from chips
chips_dir := ../../../frameworks/opt/chips/res
contacts_common_dir := ../ContactsCommon
phone_common_dir := ../PhoneCommon
stickylistheaders_dir := ../../../external/emilsjolander/stickylistheaders/library
android_joda_dir := android-joda
#ambientsdk_dir := ../../../vendor/ambient/ambientsdk/release

res_dirs := res \
	$(chips_dir) \
	$(contacts_common_dir)/res \
	$(stickylistheaders_dir)/res \
	$(android_joda_dir)/res \
	$(ambientsdk_dir)/res \
	$(phone_common_dir)/res

#	$(ambientsdk_dir)/res \

src_dirs := src \
	$(contacts_common_dir)/src \
	$(phone_common_dir)/src \
	$(stickylistheaders_dir)/src

$(shell rm -f $(LOCAL_PATH)/chips)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_PACKAGE_NAME := Mms

# Builds against the public SDK
#LOCAL_SDK_VERSION := current

LOCAL_JAVA_LIBRARIES += telephony-common
LOCAL_STATIC_JAVA_LIBRARIES += android-common jsr305
LOCAL_STATIC_JAVA_LIBRARIES += libchips
LOCAL_STATIC_JAVA_LIBRARIES += com.android.vcard libphonenumber libgeocoding guava
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-palette
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += \
	joda-time-mms \
	android-joda-mms \
#	ambientsdk

LOCAL_AAPT_FLAGS := \
	--auto-add-overlay \
	--extra-packages com.android.ex.chips \
	--extra-packages com.android.contacts.common \
	--extra-packages se.emilsjolander.stickylistheaders \
	--extra-packages net.danlew.android.joda \
	--extra-packages com.android.phone.common
#	--extra-packages com.cyanogen.ambient

LOCAL_REQUIRED_MODULES := SoundRecorder

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    joda-time-mms:libs/joda-time-2.7-no-tzdb.jar \
    android-joda-mms:android-joda/classes.jar

include $(BUILD_MULTI_PREBUILT)

# This finds and builds the test apk as well, so a single make does both.
# include $(call all-makefiles-under,$(LOCAL_PATH))