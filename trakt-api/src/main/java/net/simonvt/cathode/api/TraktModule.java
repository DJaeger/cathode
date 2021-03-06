package net.simonvt.cathode.api;

import android.content.Context;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dagger.Module;
import dagger.Provides;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.simonvt.cathode.api.entity.IsoTime;
import net.simonvt.cathode.api.enumeration.Action;
import net.simonvt.cathode.api.enumeration.CommentType;
import net.simonvt.cathode.api.enumeration.Department;
import net.simonvt.cathode.api.enumeration.Gender;
import net.simonvt.cathode.api.enumeration.GrantType;
import net.simonvt.cathode.api.enumeration.HiddenSection;
import net.simonvt.cathode.api.enumeration.ItemType;
import net.simonvt.cathode.api.enumeration.ItemTypes;
import net.simonvt.cathode.api.enumeration.Privacy;
import net.simonvt.cathode.api.enumeration.Scope;
import net.simonvt.cathode.api.enumeration.ShowStatus;
import net.simonvt.cathode.api.enumeration.TokenType;
import net.simonvt.cathode.api.service.AuthorizationService;
import net.simonvt.cathode.api.service.CheckinService;
import net.simonvt.cathode.api.service.CommentsService;
import net.simonvt.cathode.api.service.EpisodeService;
import net.simonvt.cathode.api.service.MoviesService;
import net.simonvt.cathode.api.service.PeopleService;
import net.simonvt.cathode.api.service.RecommendationsService;
import net.simonvt.cathode.api.service.SearchService;
import net.simonvt.cathode.api.service.SeasonService;
import net.simonvt.cathode.api.service.ShowsService;
import net.simonvt.cathode.api.service.SyncService;
import net.simonvt.cathode.api.service.UsersService;
import net.simonvt.cathode.api.util.OkHttpUtils;
import net.simonvt.cathode.api.util.TimeUtils;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module(library = true, complete = false)
public class TraktModule {

  //static final String API_URL = "https://api.trakt.tv";
  public static final String API_URL = "https://api-v2launch.trakt.tv";

  @Provides @Singleton @Trakt Retrofit provideRestAdapter(@Trakt OkHttpClient client,
      @Trakt Gson gson) {
    return new Retrofit.Builder() //
        .baseUrl(API_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build();
  }

  @Provides @Singleton @Trakt OkHttpClient provideOkHttpClient(Context context,
      TraktSettings settings, @Trakt List<Interceptor> interceptors) {
    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    builder.connectTimeout(15, TimeUnit.SECONDS);
    builder.readTimeout(20, TimeUnit.SECONDS);

    final File cacheDir = OkHttpUtils.getCacheDir(context);
    builder.cache(new Cache(cacheDir, OkHttpUtils.getCacheSize(cacheDir)));

    builder.interceptors().addAll(interceptors);
    builder.interceptors().add(new ApiInterceptor(settings));
    builder.interceptors().add(new AuthInterceptor(settings));
    builder.authenticator(new TraktAuthenticator(settings));

    return builder.build();
  }

  @Provides @Singleton AuthorizationService provideAuthorizationService(@Trakt Retrofit adapter) {
    return adapter.create(AuthorizationService.class);
  }

  @Provides @Singleton CheckinService provideCheckinService(@Trakt Retrofit adapter) {
    return adapter.create(CheckinService.class);
  }

  @Provides @Singleton CommentsService provideCommentsService(@Trakt Retrofit adapter) {
    return adapter.create(CommentsService.class);
  }

  @Provides @Singleton EpisodeService provideEpisodeService(@Trakt Retrofit adapter) {
    return adapter.create(EpisodeService.class);
  }

  @Provides @Singleton MoviesService provideMoviesService(@Trakt Retrofit adapter) {
    return adapter.create(MoviesService.class);
  }

  @Provides @Singleton PeopleService providePeopleService(@Trakt Retrofit adapter) {
    return adapter.create(PeopleService.class);
  }

  @Provides @Singleton RecommendationsService provideRecommendationsService(
      @Trakt Retrofit adapter) {
    return adapter.create(RecommendationsService.class);
  }

  @Provides @Singleton SearchService provideSearchService(@Trakt Retrofit adapter) {
    return adapter.create(SearchService.class);
  }

  @Provides @Singleton SeasonService provideSeasonService(@Trakt Retrofit adapter) {
    return adapter.create(SeasonService.class);
  }

  @Provides @Singleton ShowsService provideShowsService(@Trakt Retrofit adapter) {
    return adapter.create(ShowsService.class);
  }

  @Provides @Singleton SyncService provideSyncService(@Trakt Retrofit adapter) {
    return adapter.create(SyncService.class);
  }

  @Provides @Singleton UsersService provideUsersService(@Trakt Retrofit adapter) {
    return adapter.create(UsersService.class);
  }

  @Provides @Singleton @Trakt Gson provideGson() {
    GsonBuilder builder = new GsonBuilder();
    builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);

    builder.registerTypeAdapter(int.class, new IntTypeAdapter());
    builder.registerTypeAdapter(Integer.class, new IntTypeAdapter());

    builder.registerTypeAdapter(IsoTime.class, new JsonDeserializer<IsoTime>() {
      @Override
      public IsoTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
          throws JsonParseException {
        String iso = json.getAsString();
        if (iso == null) {
          return null;
        }

        long timeInMillis = TimeUtils.getMillis(iso);
        return new IsoTime(iso, timeInMillis);
      }
    });

    builder.registerTypeAdapter(GrantType.class, new JsonDeserializer<GrantType>() {
      @Override public GrantType deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return GrantType.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(TokenType.class, new JsonDeserializer<TokenType>() {
      @Override public TokenType deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return TokenType.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(Scope.class, new JsonDeserializer<Scope>() {
      @Override public Scope deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return Scope.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(ItemType.class, new JsonDeserializer<ItemType>() {
      @Override public ItemType deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return ItemType.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(Action.class, new JsonDeserializer<Action>() {
      @Override public Action deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return Action.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(ShowStatus.class, new JsonDeserializer<ShowStatus>() {
      @Override public ShowStatus deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return ShowStatus.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(Gender.class, new JsonDeserializer<Gender>() {
      @Override public Gender deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return Gender.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(Privacy.class, new JsonDeserializer<Privacy>() {
      @Override public Privacy deserialize(JsonElement jsonElement, Type type,
          JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return Privacy.fromValue(jsonElement.getAsString());
      }
    });

    builder.registerTypeAdapter(HiddenSection.class, new JsonDeserializer<HiddenSection>() {
      @Override public HiddenSection deserialize(JsonElement json, Type typeOfT,
          JsonDeserializationContext context) throws JsonParseException {
        return HiddenSection.fromValue(json.getAsString());
      }
    });

    builder.registerTypeAdapter(CommentType.class, new JsonDeserializer<CommentType>() {
      @Override public CommentType deserialize(JsonElement json, Type typeOfT,
          JsonDeserializationContext context) throws JsonParseException {
        return CommentType.fromValue(json.getAsString());
      }
    });

    builder.registerTypeAdapter(ItemTypes.class, new JsonDeserializer<ItemTypes>() {
      @Override public ItemTypes deserialize(JsonElement json, Type typeOfT,
          JsonDeserializationContext context) throws JsonParseException {
        return ItemTypes.fromValue(json.getAsString());
      }
    });

    builder.registerTypeAdapter(Department.class, new JsonDeserializer<Department>() {
      @Override public Department deserialize(JsonElement json, Type typeOfT,
          JsonDeserializationContext context) throws JsonParseException {
        return Department.fromValue(json.getAsString());
      }
    });

    return builder.create();
  }

  public static class IntTypeAdapter extends TypeAdapter<Number> {

    @Override public void write(JsonWriter out, Number value) throws IOException {
      out.value(value);
    }

    @Override public Number read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      try {
        String result = in.nextString();
        if ("".equals(result)) {
          return null;
        }
        return Integer.parseInt(result);
      } catch (NumberFormatException e) {
        throw new JsonSyntaxException(e);
      }
    }
  }
}
