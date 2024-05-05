/*
 * Created on 2024-5-5 8:06:57 AM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package androidx.media3.decoder.ffmpeg;

import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@UnstableApi
public class SimpleDecoderOutputBufferExt {
  private SimpleDecoderOutputBufferExt() {
  }

  /**
   * Grows the buffer to a new size.
   *
   * <p>Existing data is copied to the new buffer, and {@link ByteBuffer#position} is preserved.
   *
   * @param newSize New size of the buffer.
   * @return The {@link SimpleDecoderOutputBuffer#data} buffer, for convenience.
   */
  public static ByteBuffer grow(SimpleDecoderOutputBuffer outputBuffer, int newSize) {
    ByteBuffer oldData = Assertions.checkNotNull(outputBuffer.data);
    Assertions.checkArgument(newSize >= oldData.limit());
    ByteBuffer newData = ByteBuffer.allocateDirect(newSize).order(ByteOrder.nativeOrder());
    int restorePosition = oldData.position();
    oldData.position(0);
    newData.put(oldData);
    newData.position(restorePosition);
    newData.limit(newSize);
    outputBuffer.data = newData;
    return newData;
  }
}
