package edu.buffalo.cse.pocketsniffer.tasks;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.SystemClock;

import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicParameters;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicState;
import edu.buffalo.cse.phonelab.toolkit.android.periodictask.PeriodicTask;
import edu.buffalo.cse.phonelab.toolkit.android.utils.Utils;
import edu.buffalo.cse.pocketsniffer.utils.LocalUtils;


public class BuildInfoTask extends PeriodicTask<BuildInfoParameters, BuildInfoState> {

    private static final String TAG = LocalUtils.getTag(BuildInfoTask.class);

    public BuildInfoTask(Context context) {
        super(context, BuildInfoTask.class.getSimpleName());
    }

    public void check(BuildInfoParameters parameters) {
        BuildInfoState.setStatic(mContext);
        mState.uptime = SystemClock.elapsedRealtime();
        mState.rooted = Utils.isRooted();
    }

    @Override
    public BuildInfoParameters newParameters() {
        return new BuildInfoParameters();
    }

    @Override
    public BuildInfoParameters newParameters(BuildInfoParameters parameters) {
        return new BuildInfoParameters(parameters);
    }

    @Override
    public Class<BuildInfoParameters> parameterClass() {
        return BuildInfoParameters.class;
    }

    @Override
    public BuildInfoState newState() {
        return new BuildInfoState();
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}


@Root(name="BuildInfoTask", strict=false)
class BuildInfoParameters extends PeriodicParameters {

    public BuildInfoParameters() {
        super();
        this.checkIntervalSec = 900L;
    }

    public BuildInfoParameters(BuildInfoParameters parameters) {
        super(parameters);
    }
}

@Root(name="BuildInfoTask")
class BuildInfoState extends PeriodicState {

    @Element
    public static String codename;

    @Element
    public static String incremental;

    @Element
    public static String release;

    @Element
    public static Integer sdkInt;


    @Element
    public static String board;

    @Element
    public static String brand;

    @Element
    public static String device;

	@Element
	public static String build;

    @Element
    public static String hardware;

    @Element
    public static String manufacturer;

	@Element
	public static String product;
	
	@Element
	public static String model;

    @Element
    public static String radioVersion;

	@Element
	public static Integer versionCode;
	
	@Element
	public static String versionName;

    @Element
    public Long uptime;

    @Element
    public Boolean rooted;


    public BuildInfoState() {
        super();
        uptime = SystemClock.elapsedRealtime();
        rooted = Utils.isRooted();
    }

	public static void setStatic(Context context) {
        codename = Build.VERSION.CODENAME;
        incremental = Build.VERSION.INCREMENTAL;
        release = Build.VERSION.RELEASE;
        sdkInt = Build.VERSION.SDK_INT;

        board = Build.BOARD;
        brand = Build.BRAND;
        device = Build.DEVICE;
		build = Build.DISPLAY;
        hardware = Build.HARDWARE;
        manufacturer = Build.MANUFACTURER;
		product = Build.PRODUCT;
		model = Build.MODEL;
        radioVersion = Build.getRadioVersion();
		
        try {
            versionCode = Integer.parseInt(Utils.getVersionCode(context));
            versionName = Utils.getVersionName(context);
        }
        catch (NameNotFoundException e) {
            versionCode = 1;
            versionName = "1.0.0";
        }
	}
}
