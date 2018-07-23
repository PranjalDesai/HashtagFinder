package com.pranjaldesai.hashtagfinder;

import android.content.Context;
import android.content.Intent;
import android.support.constraint.ConstraintLayout;
import android.support.design.button.MaterialButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.GuestSession;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;
import com.twitter.sdk.android.core.models.Search;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.RealmModel;
import io.realm.RealmResults;
import retrofit2.Call;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.twitter_login)
    TwitterLoginButton twitterLoginButton;
    @BindView(R.id.twitter_search_container)
    ConstraintLayout searchContainer;
    @BindView(R.id.twitter_login_container)
    ConstraintLayout loginContainer;
    @BindView(R.id.searchInputLayout)
    TextInputLayout searchInputLayout;
    @BindView(R.id.searchEditText)
    TextInputEditText searchEditText;
    @BindView(R.id.searchButton)
    MaterialButton searchBtn;
    @BindView(R.id.twitter_title_text)
    TextView titleText;
    @BindView(R.id.twitter_description_text)
    TextView descriptionText;
    @BindView(R.id.searchLoading)
    ProgressBar searchProgressBar;

    TwitterSession twitterSession;
    private Realm realm;
    private final String HASHTAG= "%23";
    Snackbar snackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Twitter.initialize(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Realm.init(this);
        realm = Realm.getDefaultInstance();

        //login button sets a callback for twitter auth
        twitterLoginButton.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                if(result!= null && result.data != null){
                    twitterSession= result.data;
                    searchContainer.setVisibility(View.VISIBLE);
                    titleText.setText(getResources().getString(R.string.search_title));
                    descriptionText.setText(getResources().getString(R.string.search_description));
                    loginContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void failure(TwitterException exception) {
                snackbar= Snackbar.make(findViewById(R.id.MainActivity),exception.getMessage(), Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validateFields();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        twitterLoginButton.onActivityResult(requestCode,resultCode,data);
    }


    //dismiss the keayboard after search
    private void dismissKeyboard(){
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }
    }

    //validates fields and starts twitter API call
    private void validateFields(){
        dismissKeyboard();
        if(searchEditText.getText()==null || searchEditText.getText().toString().equals("")){
            searchInputLayout.setErrorEnabled(true);
            searchInputLayout.setError(getResources().getString(R.string.search_hint_error_text));
        }else{
            String searchText= searchEditText.getText().toString();
            String sanitizedString= searchText.replace(URLDecoder.decode(HASHTAG),"");
            searchInputLayout.setErrorEnabled(false);
            searchEditText.setFocusable(false);
            search(sanitizedString);
            searchBtn.setEnabled(false);
            searchProgressBar.setVisibility(View.VISIBLE);
        }
    }

    //Searches twitter for the specific hastag
    private void search(final String searchString){
        if(twitterSession!= null){
            if(snackbar!=null) {
                snackbar.dismiss();
            }
            TwitterApiClient twitterApiClient= TwitterCore.getInstance().getApiClient(twitterSession);
            String queryString= HASHTAG.concat(searchString);
            twitterApiClient.getSearchService().tweets(queryString,null,null,null,
                    null,30,null,null,null,true)
                    .enqueue(new Callback<Search>() {
                @Override
                public void success(Result<Search> result) {
                    if(result!=null && result.data!= null){
                        ArrayList<Tweet> tweets = convertToTweetList(result.data.tweets);
                        addToDatabase(tweets);
                        routeToResults(searchString);
                    }
                }

                @Override
                public void failure(TwitterException exception) {
                    snackbar= Snackbar.make(findViewById(R.id.MainActivity),exception.getMessage(), Snackbar.LENGTH_INDEFINITE);
                    snackbar.show();
                    searchProgressBar.setVisibility(View.GONE);
                    searchEditText.setFocusable(true);
                    searchEditText.setFocusableInTouchMode(true);
                    searchBtn.setEnabled(true);
                }
            });
        }

    }

    //Converts from Twitter Tweet format to realmObjects
    private ArrayList<Tweet> convertToTweetList(List<com.twitter.sdk.android.core.models.Tweet> tweetData){
        ArrayList<Tweet> databaseList= new ArrayList<>();
        int i=1;
        for(com.twitter.sdk.android.core.models.Tweet singleTweet: tweetData){
            Tweet realmTweet= new Tweet();
            realmTweet.setFavorite_count(singleTweet.favoriteCount);
            realmTweet.setId(singleTweet.id);
            realmTweet.setRetweet_count(singleTweet.retweetCount);
            realmTweet.setText(singleTweet.text);
            User user= new User();
            user.setName(singleTweet.user.name);
            user.setProfile_image_url_https(singleTweet.user.profileImageUrlHttps);
            user.setScreen_name(singleTweet.user.screenName);
            user.setVerified(singleTweet.user.verified);
            realmTweet.setUser(user);
            realmTweet.setCount_id(i);
            databaseList.add(realmTweet);
            i++;
        }
        return databaseList;

    }

    // adds all tweets to the database
    private void addToDatabase(ArrayList<Tweet> databaseList){
        realm.beginTransaction();
        realm.deleteAll();
        realm.insertOrUpdate(databaseList);
        realm.commitTransaction();
    }

    // navigate to the tweet view activity
    private void routeToResults(String searchQuery){
        searchProgressBar.setVisibility(View.GONE);
        Intent intent= new Intent(this, SearchResultActivity.class);
        intent.putExtra(getResources().getString(R.string.hashtag_extra), searchQuery);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        searchEditText.setFocusable(true);
        searchEditText.setFocusableInTouchMode(true);
        searchEditText.setText("");
        searchBtn.setEnabled(true);
        if(snackbar!=null) {
            snackbar.dismiss();
        }
        if(twitterSession==null){
            searchContainer.setVisibility(View.GONE);
            loginContainer.setVisibility(View.VISIBLE);
            titleText.setText(getResources().getString(R.string.login_title));
            descriptionText.setText(getResources().getString(R.string.login_description));
        }else{
            searchContainer.setVisibility(View.VISIBLE);
            loginContainer.setVisibility(View.GONE);
            titleText.setText(getResources().getString(R.string.search_title));
            descriptionText.setText(getResources().getString(R.string.search_description));
        }
    }
}
