package com.golfmarin.golf;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentActivity;
import java.util.ArrayList;

import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.TextView;
import android.os.StrictMode;




//import android.util.Log;

/**
 * An activity representing a list of Counties. This activity has different
 * presentations for handset and tablet-size devices. On handheldss, the activity
 * presents a list of items, which when touched, lead to a
 * {@link CountyDetailFragment} representing item details. On tablets, the
 * activity presents the list of items and item details side-by-side using two
 * vertical panes.
 * <p>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link CountyListFragment} and the item details (if present) is a
 * {@link CountyDetailFragment}.
 * <p>
 * This activity also implements the required
 * {@link CountyListFragment.Callbacks} interface to listen for item selections.
 */
public class RegionListActivity extends FragmentActivity implements
		RegionListFragment.Callbacks, CourseListFragment.Callbacks, Weather.Callbacks {

	/**
	 * Whether or not the activity is in two-pane mode, i.e. running on a tablet
	 * device.
	 */
	private boolean mTwoPane;
	private ArrayList<Region> regions;
	Region selectedRegion = null;
	Course selectedCourse = null;
	private ArrayList<County> counties;
	private ArrayList<Course> courses;
	WebViewFragment webViewFragment = null;
	WebView webView = null;
	Weather parser = null;
	String localWeather = new String("Getting Weather.");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build());
		super.onCreate(savedInstanceState);
		

		DataModel dm = new DataModel(this);
        regions = dm.getRegions();
		counties = dm.getCounties();
		courses = dm.getCourses();
		
		setContentView(R.layout.activity_region_list);
		this.setTitle(R.string.title_county_list);
		
		// Set the header bar, if on tablet
		TextView header = (TextView) findViewById(R.id.county_list_header);
		if (header != null)
		header.setText("Region");
		
		FragmentManager fm = getSupportFragmentManager();

		if (findViewById(R.id.detail_container) != null) {
			// The detail container view will be present only in the
			// large-screen layouts (res/values-large and
			// res/values-sw600dp). If this view is present, then the
			// activity should be in two-pane mode.
			mTwoPane = true;
			
            RegionDetailFragment df = (RegionDetailFragment) fm.findFragmentByTag("Detail");
            if (df == null) {
	            // Initialize new detail fragment
	            //Log.v("myApp", "List Activity: Initialize new detail view");
	            df = new RegionDetailFragment();
	            Bundle args = new Bundle();
	            // args.putParcelable("county", new County("Marin"));
	            args.putParcelable("region", regions.get(0));
	            args.putParcelableArrayList("courses", courses);
	            df.setArguments(args);
	            fm.beginTransaction().replace(R.id.detail_container, df, "Detail").commit();
            }  
        	else {
        		//Log.v("myApp", "List Activity, Use existing Detail Fragment " + df);	
        	}
            
            // Work on the web view
             webViewFragment = (WebViewFragment) fm.findFragmentById(R.id.web);
             webView = webViewFragment.getWebView();            
             if (parser == null) parser = new Weather(this);
             parser.getWeather(regions.get(0));
		}
         // Initialize the region list fragment
        	RegionListFragment cf = (RegionListFragment) fm.findFragmentByTag("List");
        	if ( cf == null) {
        		cf = new RegionListFragment();
            	Bundle arguments = new Bundle();
            	arguments.putParcelableArrayList("regions", regions);
            	cf.setArguments(arguments);
        		FragmentTransaction ft = fm.beginTransaction();
            	ft.replace(R.id.region_list, cf, "List");
            	//ft.addToBackStack(null);
            	ft.commit();
        	}
        	else {
        		//Log.v("myApp", "List Activity: Use existing List Fragment " + cf);
        	}

		// TODO: If exposing deep links into your app, handle intents here.
	}

	/**
	 * Callback method from {@link Region ListFragment.Callbacks} indicating that
	 * the item with the given ID was selected.
	 */
	@Override
	public void onRegionSelected(Region region) {

		selectedRegion = region;
		
		
		if (mTwoPane) {
			
			// Change the header bar
			TextView header = (TextView) findViewById(R.id.region_list_header);
			header.setText(R.string.region_list);
			
			// Replace region list fragment with course list fragment
			// for the selected county
			/*
			// Start by making a list of courses in the selected county
			ArrayList<Course> filteredCourses = new ArrayList<Course>();
			Course course;
			int i = 0;
	        while (i < courses.size()) {
	        	course = courses.get(i);
	        	if (course.county.equalsIgnoreCase(county.name)) {
		           filteredCourses.add(course);		        	
	        	}
	        	i++; 
	        }
	        */
	        // Then replace the region list fragment with the course list fragment
			FragmentManager fm = getSupportFragmentManager();
        	CourseListFragment gcf = (CourseListFragment) fm.findFragmentByTag("CourseList");
        	if ( gcf == null) {
        		gcf = new CourseListFragment();
            	Bundle arguments = new Bundle();
            	arguments.putParcelableArrayList("courses", region.courses);
            	gcf.setArguments(arguments);           	
        		//Log.v("myApp", "List Activity: Create a new Course List Fragment " + gcf);
        		FragmentTransaction ft = fm.beginTransaction();
            	ft.replace(R.id.region_list, gcf, "CourseList");
            	ft.addToBackStack(null);
            	ft.commit();
        	}
        	else {
        		//Log.v("myApp", "List Activity: Use existing course List Fragment " + gcf);
        	}
			
			// In two-pane mode, show the detail view in this activity by
			// adding or replacing the detail fragment using a
			// fragment transaction.
			Bundle arguments = new Bundle();
			arguments.putParcelable("region", selectedRegion);
	//		arguments.putParcelableArrayList(selectedCourse);
			RegionDetailFragment fragment = new RegionDetailFragment();
			fragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.detail_container, fragment).commit();

			// In two-pane mode, list items should be given the
			// 'activated' state when touched.
			((RegionListFragment) getSupportFragmentManager().findFragmentById(
					R.id.region_list)).setActivateOnItemClick(true);
			
            parser.getWeather(region);

		} else {
			// In single-pane mode, simply start the course list activity
			// for the selected county.
			Intent detailIntent = new Intent(this, CourseListActivity.class);
			detailIntent.putExtra("region", region);
            detailIntent.putExtra("courses", region.courses);
			startActivity(detailIntent);
		}
	}
	public void onCourseSelected(Course c) {

		selectedCourse = c;

		int i = 0;
        while (i < regions.size()) {
        	if (c.region.equalsIgnoreCase(regions.get(i).name)) {
	           selectedRegion = regions.get(i);
        	}
        	i++;
        }

		
		if (mTwoPane) {
			// In two-pane mode, show the detail view in this activity by
			// replacing the detail fragment using a fragment transaction.
			// In this case the detail is updated to show the selected golfcourse
		Log.v("myTag", "RegionListActivity, onCourseSelected")	;
			// Display the course detail fragment
			Bundle arguments = new Bundle();
			arguments.putParcelable("region", selectedRegion);
			arguments.putParcelable("course", selectedCourse);
         //   arguments.putParcelableArrayList("courses", courses);
			CourseDetailFragment fragment = new CourseDetailFragment();
			fragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.detail_container, fragment).commit();

		} 
		
	}

	public void onBackPressed(){
		super.onBackPressed();
		// Set the header bar
		TextView header = (TextView) findViewById(R.id.county_list_header);
		if (header != null)
		header.setText(R.string.county_list);
	}
	
	public void onLocalWeatherReady(String weather){
        webView.clearView();
        webView.loadData(weather, "text/html", null);
		
	}

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

// need to put back region list fragment
        // possibly this but invalidate the ex
        int id = item.getItemId();
     //   if (id == R.id.action_settings) {
     //       return true;
     //   }
        switch (item.getItemId()) {
            case android.R.id.home:

                NavUtils.navigateUpTo(this, new Intent(this,
                        RegionListActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
