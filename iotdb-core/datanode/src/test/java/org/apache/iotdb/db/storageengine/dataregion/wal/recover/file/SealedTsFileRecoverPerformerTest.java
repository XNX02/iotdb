/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.storageengine.dataregion.wal.recover.file;

import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;
import org.apache.iotdb.db.storageengine.dataregion.wal.utils.TsFileUtilsForRecoverTest;
import org.apache.iotdb.db.utils.EnvironmentUtils;

import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.write.WriteProcessException;
import org.apache.tsfile.file.metadata.ChunkMetadata;
import org.apache.tsfile.file.metadata.IDeviceID;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.read.TsFileSequenceReader;
import org.apache.tsfile.read.common.Chunk;
import org.apache.tsfile.read.common.Path;
import org.apache.tsfile.write.TsFileWriter;
import org.apache.tsfile.write.record.TSRecord;
import org.apache.tsfile.write.record.datapoint.DoubleDataPoint;
import org.apache.tsfile.write.record.datapoint.FloatDataPoint;
import org.apache.tsfile.write.record.datapoint.IntDataPoint;
import org.apache.tsfile.write.record.datapoint.LongDataPoint;
import org.apache.tsfile.write.schema.MeasurementSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class SealedTsFileRecoverPerformerTest {
  private static final String SG_NAME = "root.recover_sg";
  private static final IDeviceID DEVICE1_NAME =
      IDeviceID.Factory.DEFAULT_FACTORY.create(SG_NAME.concat(".d1"));
  private static final IDeviceID DEVICE2_NAME =
      IDeviceID.Factory.DEFAULT_FACTORY.create(SG_NAME.concat(".d2"));
  private static final String FILE_NAME =
      TsFileUtilsForRecoverTest.getTestTsFilePath(SG_NAME, 0, 0, 1);
  private TsFileResource tsFileResource;

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.cleanDir(new File(FILE_NAME).getParent());
  }

  @After
  public void tearDown() throws Exception {
    if (tsFileResource != null) {
      tsFileResource.close();
    }
    EnvironmentUtils.cleanDir(new File(FILE_NAME).getParent());
  }

  @Test
  public void testCompleteFileWithResource() throws Exception {
    // generate .tsfile and .resource
    File file = new File(FILE_NAME);
    generateCompleteFile(file);
    TsFileResource resourcePreparer = new TsFileResource(file);
    resourcePreparer.updateStartTime(DEVICE1_NAME, 1);
    resourcePreparer.updateEndTime(DEVICE1_NAME, 2);
    resourcePreparer.updateStartTime(DEVICE2_NAME, 3);
    resourcePreparer.updateEndTime(DEVICE2_NAME, 4);
    resourcePreparer.close();
    resourcePreparer.serialize();
    assertTrue(file.exists());
    assertTrue(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
    // recover
    tsFileResource = new TsFileResource(file);
    try (SealedTsFileRecoverPerformer recoverPerformer =
        new SealedTsFileRecoverPerformer(tsFileResource)) {
      recoverPerformer.recover();
      assertFalse(recoverPerformer.hasCrashed());
      assertFalse(recoverPerformer.canWrite());
    }
    // check file content
    TsFileSequenceReader reader = new TsFileSequenceReader(FILE_NAME);
    List<ChunkMetadata> chunkMetadataList =
        reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    reader.close();
    // check .resource file in memory
    assertEquals(1, ((long) tsFileResource.getStartTime(DEVICE1_NAME).get()));
    assertEquals(2, ((long) tsFileResource.getEndTime(DEVICE1_NAME).get()));
    assertEquals(3, ((long) tsFileResource.getStartTime(DEVICE2_NAME).get()));
    assertEquals(4, ((long) tsFileResource.getEndTime(DEVICE2_NAME).get()));
    // check file existence
    assertTrue(file.exists());
    assertTrue(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
  }

  @Test
  public void testCompleteFileWithBrokenResource() throws Exception {
    // generate .tsfile and broken .resource
    File file = new File(FILE_NAME);
    generateCompleteFile(file);
    try (OutputStream outputStream =
        new BufferedOutputStream(
            new FileOutputStream(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)))) {
      outputStream.write(1);
    }
    assertTrue(file.exists());
    assertTrue(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
    // recover
    tsFileResource = new TsFileResource(file);
    try (SealedTsFileRecoverPerformer recoverPerformer =
        new SealedTsFileRecoverPerformer(tsFileResource)) {
      recoverPerformer.recover();
      assertFalse(recoverPerformer.hasCrashed());
      assertFalse(recoverPerformer.canWrite());
    }
    // check file content
    TsFileSequenceReader reader = new TsFileSequenceReader(FILE_NAME);
    List<ChunkMetadata> chunkMetadataList =
        reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    reader.close();
    // check .resource file in memory
    assertEquals(1, ((long) tsFileResource.getStartTime(DEVICE1_NAME).get()));
    assertEquals(2, ((long) tsFileResource.getEndTime(DEVICE1_NAME).get()));
    assertEquals(3, ((long) tsFileResource.getStartTime(DEVICE2_NAME).get()));
    assertEquals(4, ((long) tsFileResource.getEndTime(DEVICE2_NAME).get()));
    // check file existence
    assertTrue(file.exists());
    assertTrue(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
  }

  @Test
  public void testCompleteFileWithoutResource() throws Exception {
    // generate .tsfile
    File file = new File(FILE_NAME);
    generateCompleteFile(file);
    assertTrue(file.exists());
    assertFalse(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
    // recover
    tsFileResource = new TsFileResource(file);
    try (SealedTsFileRecoverPerformer recoverPerformer =
        new SealedTsFileRecoverPerformer(tsFileResource)) {
      recoverPerformer.recover();
      assertFalse(recoverPerformer.hasCrashed());
      assertFalse(recoverPerformer.canWrite());
    }
    // check file content
    TsFileSequenceReader reader = new TsFileSequenceReader(FILE_NAME);
    List<ChunkMetadata> chunkMetadataList =
        reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    reader.close();
    // check .resource file in memory
    assertEquals(1, ((long) tsFileResource.getStartTime(DEVICE1_NAME).get()));

    assertEquals(2, ((long) tsFileResource.getEndTime(DEVICE1_NAME).get()));
    assertEquals(3, ((long) tsFileResource.getStartTime(DEVICE2_NAME).get()));
    assertEquals(4, ((long) tsFileResource.getEndTime(DEVICE2_NAME).get()));
    // check file existence
    assertTrue(file.exists());
    assertTrue(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
  }

  private void generateCompleteFile(File tsFile) throws IOException, WriteProcessException {
    try (TsFileWriter writer = new TsFileWriter(tsFile)) {
      writer.registerTimeseries(
          new Path(DEVICE1_NAME), new MeasurementSchema("s1", TSDataType.INT32, TSEncoding.RLE));
      writer.registerTimeseries(
          new Path(DEVICE1_NAME), new MeasurementSchema("s2", TSDataType.INT64, TSEncoding.RLE));
      writer.registerTimeseries(
          new Path(DEVICE2_NAME), new MeasurementSchema("s1", TSDataType.FLOAT, TSEncoding.RLE));
      writer.registerTimeseries(
          new Path(DEVICE2_NAME), new MeasurementSchema("s2", TSDataType.DOUBLE, TSEncoding.RLE));
      writer.writeRecord(
          new TSRecord(DEVICE1_NAME, 1)
              .addTuple(new IntDataPoint("s1", 1))
              .addTuple(new LongDataPoint("s2", 1)));
      writer.writeRecord(
          new TSRecord(DEVICE1_NAME, 2)
              .addTuple(new IntDataPoint("s1", 2))
              .addTuple(new LongDataPoint("s2", 2)));
      writer.writeRecord(
          new TSRecord(DEVICE2_NAME, 3)
              .addTuple(new FloatDataPoint("s1", 3))
              .addTuple(new DoubleDataPoint("s2", 3)));
      writer.writeRecord(
          new TSRecord(DEVICE2_NAME, 4)
              .addTuple(new FloatDataPoint("s1", 4))
              .addTuple(new DoubleDataPoint("s2", 4)));
    }
  }

  @Test
  public void testCrashedFile() throws Exception {
    // generate crashed .tsfile
    File file = new File(FILE_NAME);
    generateCrashedFile(file);
    assertTrue(file.exists());
    assertFalse(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
    // recover
    tsFileResource = new TsFileResource(file);
    try (SealedTsFileRecoverPerformer recoverPerformer =
        new SealedTsFileRecoverPerformer(tsFileResource)) {
      recoverPerformer.recover();
      assertTrue(recoverPerformer.hasCrashed());
      assertFalse(recoverPerformer.canWrite());
    }
    // check file content
    TsFileSequenceReader reader = new TsFileSequenceReader(FILE_NAME);
    List<ChunkMetadata> chunkMetadataList =
        reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE1_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s1", true));
    assertNotNull(chunkMetadataList);
    chunkMetadataList = reader.getChunkMetadataList(new Path(DEVICE2_NAME, "s2", true));
    assertNotNull(chunkMetadataList);
    assertEquals(1, chunkMetadataList.size());
    Chunk chunk = reader.readMemChunk(chunkMetadataList.get(0));
    assertEquals(3, chunk.getChunkStatistic().getEndTime());
    reader.close();
    // check .resource file in memory
    assertEquals(1, ((long) tsFileResource.getStartTime(DEVICE1_NAME).get()));
    assertEquals(2, ((long) tsFileResource.getEndTime(DEVICE1_NAME).get()));
    assertEquals(3, ((long) tsFileResource.getStartTime(DEVICE2_NAME).get()));
    assertEquals(3, ((long) tsFileResource.getEndTime(DEVICE2_NAME).get()));
    // check file existence
    assertTrue(file.exists());
    assertTrue(new File(FILE_NAME.concat(TsFileResource.RESOURCE_SUFFIX)).exists());
  }

  private void generateCrashedFile(File tsFile) throws IOException, WriteProcessException {
    long truncateSize;
    try (TsFileWriter writer = new TsFileWriter(tsFile)) {
      writer.registerTimeseries(
          new Path(DEVICE1_NAME), new MeasurementSchema("s1", TSDataType.INT32, TSEncoding.RLE));
      writer.registerTimeseries(
          new Path(DEVICE1_NAME), new MeasurementSchema("s2", TSDataType.INT64, TSEncoding.RLE));
      writer.registerTimeseries(
          new Path(DEVICE2_NAME), new MeasurementSchema("s1", TSDataType.FLOAT, TSEncoding.RLE));
      writer.registerTimeseries(
          new Path(DEVICE2_NAME), new MeasurementSchema("s2", TSDataType.DOUBLE, TSEncoding.RLE));
      writer.writeRecord(
          new TSRecord(DEVICE1_NAME, 1)
              .addTuple(new IntDataPoint("s1", 1))
              .addTuple(new LongDataPoint("s2", 1)));
      writer.writeRecord(
          new TSRecord(DEVICE1_NAME, 2)
              .addTuple(new IntDataPoint("s1", 2))
              .addTuple(new LongDataPoint("s2", 2)));
      writer.writeRecord(
          new TSRecord(DEVICE2_NAME, 3)
              .addTuple(new FloatDataPoint("s1", 3))
              .addTuple(new DoubleDataPoint("s2", 3)));
      writer.flush();
      try (FileChannel channel = new FileInputStream(tsFile).getChannel()) {
        truncateSize = channel.size();
      }
      writer.writeRecord(
          new TSRecord(DEVICE2_NAME, 4)
              .addTuple(new FloatDataPoint("s1", 4))
              .addTuple(new DoubleDataPoint("s2", 4)));
      writer.flush();
      try (FileChannel channel = new FileInputStream(tsFile).getChannel()) {
        truncateSize = (truncateSize + channel.size()) / 2;
      }
    }
    try (FileChannel channel = new FileOutputStream(tsFile, true).getChannel()) {
      channel.truncate(truncateSize);
    }
  }
}
