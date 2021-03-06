// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.core.term.AppTerm;
import org.aya.core.term.UnivTerm;
import org.aya.core.visitor.Substituter;
import org.aya.test.Lisp;
import org.aya.test.LispTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubstTest extends LispTestCase {
  @Test
  public void emptySubst() {
    var term = Lisp.parse("(app tony lambda)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(Substituter.TermSubst.EMPTY));
  }

  @Test
  public void unrelatedSubst() {
    var term = Lisp.parse("(app beta lambda)");
    assertTrue(term instanceof AppTerm);
    assertEquals(term, term.subst(new Substituter.TermSubst(() -> "lambda", UnivTerm.OMEGA)));
  }

  @Test
  public void relatedSubst() {
    var term = Lisp.parse("(app tony beta)", vars);
    assertTrue(term instanceof AppTerm);
    assertNotEquals(term, term.subst(new Substituter.TermSubst(vars.get("beta"), UnivTerm.OMEGA)));
  }
}
