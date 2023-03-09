# Installation
Step 1. Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			maven { url 'https://jitpack.io' }
		}
	}
 
Step 2. Add the dependency

	dependencies {
	        implementation 'com.github.mowlashuvo:skeleton-view:1.1.0'
		}

Step 3. Add This code 

    <com.crackpot.skeleton_view.SkeletonLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        app:skeleton_auto_start="true"
        app:skeleton_angle="90"
        app:skeleton_color="#cccccc"
        app:skeleton_animation_duration="2000">

        <View
            android:layout_gravity="center"
            android:background="#e6e6e6"
            android:layout_width="50dp"
            android:layout_height="15dp"/>

    </com.crackpot.skeleton_view.SkeletonLayout>
