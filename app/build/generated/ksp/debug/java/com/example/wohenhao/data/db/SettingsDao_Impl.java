package com.example.wohenhao.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SettingsDao_Impl implements SettingsDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AppSettings> __insertionAdapterOfAppSettings;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public SettingsDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAppSettings = new EntityInsertionAdapter<AppSettings>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `app_settings` (`key`,`value`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AppSettings entity) {
        statement.bindString(1, entity.getKey());
        statement.bindString(2, entity.getValue());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM app_settings WHERE `key` = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AppSettings setting, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAppSettings.insert(setting);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String key, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, key);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getSetting(final String key, final Continuation<? super AppSettings> $completion) {
    final String _sql = "SELECT * FROM app_settings WHERE `key` = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AppSettings>() {
      @Override
      @Nullable
      public AppSettings call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final AppSettings _result;
          if (_cursor.moveToFirst()) {
            final String _tmpKey;
            _tmpKey = _cursor.getString(_cursorIndexOfKey);
            final String _tmpValue;
            _tmpValue = _cursor.getString(_cursorIndexOfValue);
            _result = new AppSettings(_tmpKey,_tmpValue);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public LiveData<AppSettings> getSettingLive(final String key) {
    final String _sql = "SELECT * FROM app_settings WHERE `key` = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    return __db.getInvalidationTracker().createLiveData(new String[] {"app_settings"}, false, new Callable<AppSettings>() {
      @Override
      @Nullable
      public AppSettings call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final AppSettings _result;
          if (_cursor.moveToFirst()) {
            final String _tmpKey;
            _tmpKey = _cursor.getString(_cursorIndexOfKey);
            final String _tmpValue;
            _tmpValue = _cursor.getString(_cursorIndexOfValue);
            _result = new AppSettings(_tmpKey,_tmpValue);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllSettings(final Continuation<? super List<AppSettings>> $completion) {
    final String _sql = "SELECT * FROM app_settings";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AppSettings>>() {
      @Override
      @NonNull
      public List<AppSettings> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final List<AppSettings> _result = new ArrayList<AppSettings>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AppSettings _item;
            final String _tmpKey;
            _tmpKey = _cursor.getString(_cursorIndexOfKey);
            final String _tmpValue;
            _tmpValue = _cursor.getString(_cursorIndexOfValue);
            _item = new AppSettings(_tmpKey,_tmpValue);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getBoolean(final String key, final boolean defaultValue,
      final Continuation<? super Boolean> $completion) {
    return SettingsDao.DefaultImpls.getBoolean(SettingsDao_Impl.this, key, defaultValue, $completion);
  }

  @Override
  public Object getInt(final String key, final int defaultValue,
      final Continuation<? super Integer> $completion) {
    return SettingsDao.DefaultImpls.getInt(SettingsDao_Impl.this, key, defaultValue, $completion);
  }

  @Override
  public Object getLong(final String key, final long defaultValue,
      final Continuation<? super Long> $completion) {
    return SettingsDao.DefaultImpls.getLong(SettingsDao_Impl.this, key, defaultValue, $completion);
  }

  @Override
  public Object getDouble(final String key, final double defaultValue,
      final Continuation<? super Double> $completion) {
    return SettingsDao.DefaultImpls.getDouble(SettingsDao_Impl.this, key, defaultValue, $completion);
  }

  @Override
  public Object getString(final String key, final String defaultValue,
      final Continuation<? super String> $completion) {
    return SettingsDao.DefaultImpls.getString(SettingsDao_Impl.this, key, defaultValue, $completion);
  }

  @Override
  public Object putBoolean(final String key, final boolean value,
      final Continuation<? super Unit> $completion) {
    return SettingsDao.DefaultImpls.putBoolean(SettingsDao_Impl.this, key, value, $completion);
  }

  @Override
  public Object putInt(final String key, final int value,
      final Continuation<? super Unit> $completion) {
    return SettingsDao.DefaultImpls.putInt(SettingsDao_Impl.this, key, value, $completion);
  }

  @Override
  public Object putLong(final String key, final long value,
      final Continuation<? super Unit> $completion) {
    return SettingsDao.DefaultImpls.putLong(SettingsDao_Impl.this, key, value, $completion);
  }

  @Override
  public Object putDouble(final String key, final double value,
      final Continuation<? super Unit> $completion) {
    return SettingsDao.DefaultImpls.putDouble(SettingsDao_Impl.this, key, value, $completion);
  }

  @Override
  public Object putString(final String key, final String value,
      final Continuation<? super Unit> $completion) {
    return SettingsDao.DefaultImpls.putString(SettingsDao_Impl.this, key, value, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
