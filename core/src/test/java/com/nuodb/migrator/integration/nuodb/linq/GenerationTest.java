/**
 * Copyright (c) 2015, NuoDB, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of NuoDB, Inc. nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NUODB, INC. BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nuodb.migrator.integration.nuodb.linq;

import java.sql.PreparedStatement;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.nuodb.migrator.integration.MigrationTestBase;
import com.nuodb.migrator.integration.nuodb.linq.util.DatabaseUtil;

@Test(groups = { "nuodblinqtest" }, dependsOnGroups = { "linqdataloadperformed" })
public class GenerationTest extends MigrationTestBase {
    PreparedStatement pstmt = null;

    public void oddOrEvenInRange() throws Exception {
        List<Integer> list = DatabaseUtil.getNumArray("SELECT num FROM array WHERE cast (num as int) % 2 = 0",
                nuodbConnection, pstmt);
        Assert.assertTrue(list.size() >= 1, "The list is empty");
        Integer[] acteven = list.toArray(new Integer[list.size()]);
        List<Integer> list1 = DatabaseUtil.getNumArray("SELECT num FROM array WHERE cast (num as int) % 2 = 1",
                nuodbConnection, pstmt);
        Assert.assertTrue(list1.size() >= 1, "The list1 is empty");
        Integer[] actodd = list1.toArray(new Integer[list1.size()]);
        Integer expodd[] = { 5, 1, 3, 9, 7 };
        Integer expeven[] = { 4, 8, 6, 2, 0 };
        Assert.assertEqualsNoOrder(acteven, expeven, "Even List condains odd number");
        Assert.assertEqualsNoOrder(actodd, expodd, "Odd List condains even number");
    }
}
