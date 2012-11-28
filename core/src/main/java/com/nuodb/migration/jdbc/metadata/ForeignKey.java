/**
 * Copyright (c) 2012, NuoDB, Inc.
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
package com.nuodb.migration.jdbc.metadata;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Map;

/**
 * @author Sergey Bushik
 */
public class ForeignKey extends ConstraintBase {

    private Map<Integer, ForeignKeyReference> references = Maps.newTreeMap();
    private ReferentialAction updateAction = ReferentialAction.NO_ACTION;
    private ReferentialAction deleteAction = ReferentialAction.NO_ACTION;
    private Deferrability deferrability;

    public ForeignKey(Identifier name) {
        super(name);
    }

    @Override
    public Collection<Column> getColumns() {
        return getTargetColumns();
    }

    public void addReference(Column sourceColumn, Column targetColumn, int position) {
        references.put(position, new ForeignKeyReference(sourceColumn, targetColumn));
    }

    public ReferentialAction getUpdateAction() {
        return updateAction;
    }

    public void setUpdateAction(ReferentialAction updateAction) {
        this.updateAction = updateAction;
    }

    public ReferentialAction getDeleteAction() {
        return deleteAction;
    }

    public void setDeleteAction(ReferentialAction deleteAction) {
        this.deleteAction = deleteAction;
    }

    public Deferrability getDeferrability() {
        return deferrability;
    }

    public void setDeferrability(Deferrability deferrability) {
        this.deferrability = deferrability;
    }

    public Collection<ForeignKeyReference> getReferences() {
        return references.values();
    }

    public Collection<Column> getSourceColumns() {
        return Collections2.transform(getReferences(), new Function<ForeignKeyReference, Column>() {
            @Override
            public Column apply(ForeignKeyReference input) {
                return input.getSourceColumn();
            }
        });
    }

    public Collection<Column> getTargetColumns() {
        return Collections2.transform(getReferences(), new Function<ForeignKeyReference, Column>() {
            @Override
            public Column apply(ForeignKeyReference input) {
                return input.getTargetColumn();
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ForeignKey that = (ForeignKey) o;

        if (deferrability != that.deferrability) return false;
        if (deleteAction != that.deleteAction) return false;
        if (references != null ? !references.equals(that.references) : that.references != null) return false;
        if (updateAction != that.updateAction) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (references != null ? references.hashCode() : 0);
        result = 31 * result + (updateAction != null ? updateAction.hashCode() : 0);
        result = 31 * result + (deleteAction != null ? deleteAction.hashCode() : 0);
        result = 31 * result + (deferrability != null ? deferrability.hashCode() : 0);
        return result;
    }

    @Override
    public void output(int indent, StringBuilder buffer) {
        super.output(indent, buffer);

        buffer.append(' ');
        output(indent, buffer, getReferences());
    }
}
