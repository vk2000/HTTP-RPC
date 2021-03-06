/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.httprpc.sql;

import java.sql.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.httprpc.AbstractTest;
import org.httprpc.beans.BeanAdapter;
import org.httprpc.sql.ResultSetAdapter;
import org.junit.Assert;
import org.junit.Test;

public class ResultSetAdapterTest extends AbstractTest {
    @Test
    public void testResultSetAdapter1() throws SQLException {
        LinkedList<Map<String, Object>> list = new LinkedList<>();

        try (TestResultSet resultSet = new TestResultSet()) {
            ResultSetAdapter adapter = new ResultSetAdapter(resultSet);

            for (Map<String, Object> row : adapter) {
                HashMap<String, Object> map = new HashMap<>();

                map.putAll(row);

                list.add(map);
            }
        }

        Assert.assertEquals(listOf(
            mapOf(
                entry("a", 2L),
                entry("b", 3.0),
                entry("c", true),
                entry("d", "abc"),
                entry("e", new Date(0))
            ),
            mapOf(
                entry("a", 4L),
                entry("b", 6.0),
                entry("c", false),
                entry("d", "def"),
                entry("e", new Date(0))
            ),
            mapOf(
                entry("a", 8L),
                entry("b", 9.0),
                entry("c", false),
                entry("d", "ghi"),
                entry("e", null)
            )
        ), list);
    }

    @Test
    public void testResultSetAdapter2() throws SQLException {
        LinkedList<Map<String, Object>> list = new LinkedList<>();

        try (TestResultSet resultSet = new TestResultSet()) {
            for (TestRow row : ResultSetAdapter.adapt(resultSet, TestRow.class)) {
                HashMap<String, Object> map = new HashMap<>();

                map.putAll(new BeanAdapter(row));

                list.add(map);
            }
        }

        Assert.assertEquals(listOf(
            mapOf(
                entry("a", 2L),
                entry("b", 3.0),
                entry("c", true),
                entry("d", "abc"),
                entry("e", new Date(0))
            ),
            mapOf(
                entry("a", 4L),
                entry("b", 6.0),
                entry("c", false),
                entry("d", "def"),
                entry("e", new Date(0))
            ),
            mapOf(
                entry("a", 8L),
                entry("b", 9.0),
                entry("c", false),
                entry("d", "ghi"),
                entry("e", null)
            )
        ), list);
    }
}
