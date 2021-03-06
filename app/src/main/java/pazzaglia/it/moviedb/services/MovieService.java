package pazzaglia.it.moviedb.services;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.util.Log;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pazzaglia.it.moviedb.data.MovieColumns;
import pazzaglia.it.moviedb.data.MovieProvider;
import pazzaglia.it.moviedb.models.Movie;
import pazzaglia.it.moviedb.models.Movies;
import pazzaglia.it.moviedb.networks.AbstractApiCaller;
import pazzaglia.it.moviedb.networks.PopularMoviesCaller;
import pazzaglia.it.moviedb.networks.TopRatedMoviesCaller;
import pazzaglia.it.moviedb.utils.Constant;

import static pazzaglia.it.moviedb.utils.Constant.EXTRA_MOVIE_SORTING;
import static pazzaglia.it.moviedb.utils.Constant.SORTING_POPULAR;


public class MovieService extends IntentService {

    private static final String TAG = "MovieService";
    private List<Integer> idList;
    private List<Integer> idListWithoutImage;

    public MovieService() {
        super("MovieService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        idListWithoutImage = new ArrayList<Integer>();;
        idList = new ArrayList<Integer>();

        loadStoredMovieId();
        int movieOrder = intent.getIntExtra(EXTRA_MOVIE_SORTING, SORTING_POPULAR);

        if(movieOrder == SORTING_POPULAR){
            loadPopularMovies();
        }else {
            loadTopRatedMovies();
        }
    }

    private void loadStoredMovieId(){
        Cursor c = getContentResolver().query(MovieProvider.Movies.CONTENT_URI,
                new String[]{MovieColumns._ID, MovieColumns.POSTER_BLOB}, null, null, null);

        while (c.moveToNext()){
            idList.add(c.getInt(c.getColumnIndex(MovieColumns._ID)));
            if(c.getBlob((c.getColumnIndex(MovieColumns.POSTER_BLOB))) == null){
                idListWithoutImage.add(c.getInt(c.getColumnIndex(MovieColumns._ID)));
            }
        }

        c.close();

    }

    private void loadPopularMovies(){
        PopularMoviesCaller popularMoviesCaller = new PopularMoviesCaller();
        popularMoviesCaller.doApiCall(getApplicationContext(), "Loading popular Movies", 0, apiCallback);
    }

    private void loadTopRatedMovies(){
        TopRatedMoviesCaller topRatedMoviesCaller = new TopRatedMoviesCaller();
        topRatedMoviesCaller.doApiCall(getApplicationContext(), "Loading top rated Movies", 0, apiCallback);
    }


    private AbstractApiCaller.MyCallbackInterface apiCallback = new AbstractApiCaller.MyCallbackInterface<Movies>() {
        @Override
        public void onDownloadFinishedOK(Movies result) {
            insertMovies(result);
        }
        @Override
        public void onDownloadFinishedKO(Movies result) {

        }
    };


    public void insertMovies(Movies movies){
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();

        //insert only new movie
        for (Movie movie : movies.getResults()){
            ContentProviderOperation.Builder builder;
            int movieId = movie.getId();
            if(!idList.contains(movieId)) {
                builder = ContentProviderOperation.newInsert(
                        MovieProvider.Movies.CONTENT_URI);
                builder.withValue(MovieColumns._ID, movieId);
                builder.withValue(MovieColumns.POSTER_PATH, movie.getPosterPath());
                builder.withValue(MovieColumns.POPULARITY, movie.getPopularity());
                builder.withValue(MovieColumns.ORIGINAL_TITLE, movie.getOriginalTitle());
                builder.withValue(MovieColumns.VOTE_AVERAGE, movie.getVoteAverage());
                builder.withValue(MovieColumns.RELEASE_DATE, movie.getReleaseDate());
                builder.withValue(MovieColumns.OVERVIEW, movie.getOverview());
                builder.withValue(MovieColumns.FAVOURITE, 0);

            }  else {
                builder = ContentProviderOperation.newUpdate(
                        MovieProvider.Movies.CONTENT_URI);
                builder.withSelection(MovieColumns._ID +" = ?",  new String[]{String.valueOf(movieId)});
            }

            if(!idListWithoutImage.contains(movieId)) {
                Picasso.with(getApplicationContext()) //
                        .load(Constant.BASE_IMG_URL + movie.getPosterPath()) //
                        .into(getTarget(movieId));
            }

            builder.withValue(MovieColumns.POPULARITY, movie.getPopularity());
            builder.withValue(MovieColumns.VOTE_AVERAGE, movie.getVoteAverage());

            batchOperations.add(builder.build());
        }

        try{
            getContentResolver().applyBatch(MovieProvider.AUTHORITY, batchOperations);
        } catch(RemoteException | OperationApplicationException e){
            Log.e(TAG, "Error applying batch insert", e);
        }

    }

    private  Target getTarget(final int movieId){
        Target target = new Target(){

        @Override
        public void onBitmapLoaded(final Bitmap bitmap, final Picasso.LoadedFrom from) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    Picasso.LoadedFrom innerFrom = from;
                    byte[] data = getBitmapAsByteArray(bitmap);
                    ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(
                            MovieProvider.Movies.CONTENT_URI);

                    builder.withSelection(MovieColumns._ID +" = ?",  new String[]{String.valueOf(movieId)});
                    builder.withValue(MovieColumns.POSTER_BLOB, data);
                    try{
                        getContentResolver().applyBatch(MovieProvider.AUTHORITY, new ArrayList<ContentProviderOperation>(Arrays.asList(builder.build())));
                    } catch(RemoteException | OperationApplicationException e){
                        Log.e(TAG, "Error applying batch insert", e);
                    }
                }
            }).start();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {}

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            if (placeHolderDrawable != null) {}
        }
    };

        return target;
    }


    public static byte[] getBitmapAsByteArray(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        return outputStream.toByteArray();
    }

}
