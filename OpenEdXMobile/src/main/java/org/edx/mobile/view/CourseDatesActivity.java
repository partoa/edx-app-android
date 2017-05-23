package org.edx.mobile.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import org.edx.mobile.R;
import org.edx.mobile.base.BaseSingleFragmentActivity;
import org.edx.mobile.model.api.EnrolledCoursesResponse;
import org.edx.mobile.util.FileUtil;

import java.io.IOException;

import static org.edx.mobile.view.Router.EXTRA_BUNDLE;
import static org.edx.mobile.view.Router.EXTRA_COURSE_DATA;

public class CourseDatesActivity extends BaseSingleFragmentActivity {
    private EnrolledCoursesResponse courseData;

    public static Intent newIntent(@NonNull Context context, @NonNull EnrolledCoursesResponse model) {
        final Bundle bundle = new Bundle();
        bundle.putSerializable(EXTRA_COURSE_DATA, model);
        final Intent intent = new Intent(context, CourseDatesActivity.class);
        intent.putExtra(EXTRA_BUNDLE, bundle);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //This activity should not contain the drawer(Navigation Fragment).
        blockDrawerFromOpening();

        setTitle(R.string.label_course_dates);

        final Bundle bundle = savedInstanceState != null ? savedInstanceState :
                getIntent().getBundleExtra(EXTRA_BUNDLE);
        courseData = (EnrolledCoursesResponse) bundle.getSerializable(EXTRA_COURSE_DATA);
    }

    @Override
    public Fragment getFirstFragment() {
        final StringBuilder courseInfoUrl = new StringBuilder(64);
        courseInfoUrl.append(environment.getConfig().getApiHostURL())
                .append("/courses/")
                .append(courseData.getCourse().getId())
                .append("/info");
        String javascript;
        try {
            javascript = FileUtil.loadTextFileFromAssets(this, "js/important_course_dates.js");
        } catch (IOException e) {
            logger.error(e);
            javascript = null;
        }
        return AuthenticatedWebViewFragment.newInstance(courseInfoUrl.toString(), javascript);
    }
}
