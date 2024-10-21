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

package org.apache.iotdb.db.queryengine.execution.operator.process.gapfill;

import org.apache.iotdb.db.queryengine.execution.operator.Operator;
import org.apache.iotdb.db.queryengine.execution.operator.OperatorContext;
import org.apache.iotdb.db.utils.datastructure.SortKey;

import org.apache.tsfile.enums.TSDataType;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

// with group
abstract class AbstractGapFillWGroupOperator extends AbstractGapFillOperator {

  private final Comparator<SortKey> groupKeyComparator;
  private final Set<Integer> groupKeyIndexSet;

  AbstractGapFillWGroupOperator(
      OperatorContext operatorContext,
      Operator child,
      int timeColumnIndex,
      long startTime,
      long endTime,
      Comparator<SortKey> groupKeyComparator,
      List<TSDataType> dataTypes,
      Set<Integer> groupKeyIndexSet) {
    super(operatorContext, child, timeColumnIndex, startTime, endTime, dataTypes);
    this.groupKeyComparator = groupKeyComparator;
    this.groupKeyIndexSet = groupKeyIndexSet;
  }

  @Override
  boolean isNewGroup(SortKey currentGroupKey, SortKey previousGroupKey) {
    return previousGroupKey != null
        && groupKeyComparator.compare(previousGroupKey, currentGroupKey) != 0;
  }

  @Override
  boolean isGroupKeyColumn(int index) {
    return groupKeyIndexSet.contains(index);
  }
}