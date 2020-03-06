/*
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.njovy.common.file;

import android.content.Context;
import android.text.TextUtils;
import com.njovy.common.internal.Preconditions;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Static operations on {@link File}s
 */
public class FileUtils {

  /**
   * Creates the specified directory, along with all parent paths if necessary
   *
   * @param directory directory to be created
   * @throws CreateDirectoryException
   */
  public static void mkdirs(File directory) throws CreateDirectoryException {
    if (directory.exists()) {
      // file exists and *is* a directory
      if (directory.isDirectory()) {
        return;
      }

      // file exists, but is not a directory - delete it
      if (!directory.delete()) {
        throw new CreateDirectoryException(directory.getAbsolutePath(),
            new FileDeleteException(directory.getAbsolutePath()));
      }
    }

    // doesn't exist. Create one
    if (!directory.mkdirs() && !directory.isDirectory()) {
      throw new CreateDirectoryException(directory.getAbsolutePath());
    }
  }

  /**
   * Cleans a directory without deleting it.
   *
   * @param directory directory to clean
   * @throws IOException in case cleaning is unsuccessful
   */
  public static void cleanDirectory(final File directory) throws IOException {
    if (!directory.exists()) {
      final String message = directory + " does not exist";
      throw new IllegalArgumentException(message);
    }

    if (!directory.isDirectory()) {
      final String message = directory + " is not a directory";
      throw new IllegalArgumentException(message);
    }

    final File[] files = directory.listFiles();
    if (files == null) {  // null if security restricted
      throw new IOException("Failed to list contents of " + directory);
    }

    IOException exception = null;
    for (final File file : files) {
      try {
        forceDelete(file);
      } catch (final IOException ioe) {
        exception = ioe;
      }
    }

    if (null != exception) {
      throw exception;
    }
  }

  //-----------------------------------------------------------------------

  /**
   * Deletes a directory recursively.
   *
   * @param directory directory to delete
   * @throws IOException in case deletion is unsuccessful
   */
  public static void deleteDirectory(final File directory) throws IOException {
    if (!directory.exists()) {
      return;
    }

    if (!directory.delete()) {
      final String message = "Unable to delete directory " + directory + ".";
      throw new IOException(message);
    }
  }

  //-----------------------------------------------------------------------

  /**
   * Deletes a file. If file is a directory, delete it and all sub-directories.
   * <p>
   * The difference between File.delete() and this method are:
   * <ul>
   * <li>A directory to be deleted does not have to be empty.</li>
   * <li>You get exceptions when a file or directory cannot be deleted.
   * (java.io.File methods returns a boolean)</li>
   * </ul>
   *
   * @param file file or directory to delete, must not be {@code null}
   * @throws NullPointerException if the directory is {@code null}
   * @throws FileNotFoundException if the file was not found
   * @throws IOException in case deletion is unsuccessful
   */
  public static void forceDelete(final File file) throws IOException {
    if (file.isDirectory()) {
      deleteDirectory(file);
    } else {
      final boolean filePresent = file.exists();
      if (!file.delete()) {
        if (!filePresent) {
          throw new FileNotFoundException("File does not exist: " + file);
        }
        final String message = "Unable to delete file: " + file;
        throw new IOException(message);
      }
    }
  }

  /**
   * Renames the source file to the target file. If the target file exists, then we attempt to
   * delete it. If the delete or the rename operation fails, then we raise an exception
   *
   * @param source the source file
   * @param target the new 'name' for the source file
   * @throws IOException
   */
  public static void rename(File source, File target) throws RenameException {
    Preconditions.checkNotNull(source);
    Preconditions.checkNotNull(target);

    // delete the target first - but ignore the result
    target.delete();

    if (source.renameTo(target)) {
      return;
    }

    Throwable innerException = null;
    if (target.exists()) {
      innerException = new FileDeleteException(target.getAbsolutePath());
    } else if (!source.getParentFile().exists()) {
      innerException = new ParentDirNotFoundException(source.getAbsolutePath());
    } else if (!source.exists()) {
      innerException = new FileNotFoundException(source.getAbsolutePath());
    }

    throw new RenameException(
        "Unknown error renaming " + source.getAbsolutePath() + " to " + target.getAbsolutePath(),
        innerException);
  }

  public static File cacheDir(Context context) {
    File cacheDir = context.getExternalCacheDir();
    if (cacheDir != null) {
      return cacheDir;
    }
    cacheDir = context.getCacheDir();
    if (cacheDir != null) {
      return cacheDir;
    }
    return context.getDir("cache", Context.MODE_PRIVATE);
  }

  public static File tempFileInCache(Context context, String name) {
    File file = new File(FileUtils.cacheDir(context), name);
    if (!file.exists()) {
      try {
        file.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
    return file;
  }

  public static void copy(File source, File dest) throws IOException {
    if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();

    if (!dest.exists()) {
      dest.createNewFile();
    }

    FileChannel in = null;
    FileChannel out = null;

    try {
      in = new FileInputStream(source).getChannel();
      out = new FileOutputStream(dest).getChannel();
      out.transferFrom(in, 0, in.size());
    } finally {
      if (source != null) {
        in.close();
      }
      if (out != null) {
        out.close();
      }
    }
  }

  public static void deleteQuietly(String path) {
    if (!TextUtils.isEmpty(path)) deleteQuietly(new File(path));
  }

  public static void deleteQuietly(File file) {
    if (file != null && file.exists()) {
      try {
        if (file.isDirectory()) {
          cleanDirectory(file);
        }
      } catch (final Exception ignored) {
      }

      try {
        file.delete();
      } catch (final Exception ignored) {
      }
    }
  }

  /**
   * Represents an exception during directory creation
   */
  public static class CreateDirectoryException extends IOException {
    public CreateDirectoryException(String message) {
      super(message);
    }

    public CreateDirectoryException(String message, Throwable innerException) {
      super(message);
      initCause(innerException);
    }
  }

  /**
   * A specialization of FileNotFoundException when the parent-dir doesn't exist
   */
  public static class ParentDirNotFoundException extends FileNotFoundException {
    public ParentDirNotFoundException(String message) {
      super(message);
    }
  }

  /**
   * Represents an exception when the target file/directory cannot be deleted
   */
  public static class FileDeleteException extends IOException {
    public FileDeleteException(String message) {
      super(message);
    }
  }

  /**
   * Represents an unknown rename exception
   */
  public static class RenameException extends IOException {
    public RenameException(String message) {
      super(message);
    }

    public RenameException(String message, Throwable innerException) {
      super(message);
      initCause(innerException);
    }
  }
}
