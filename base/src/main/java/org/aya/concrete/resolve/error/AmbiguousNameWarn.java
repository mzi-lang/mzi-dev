// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record AmbiguousNameWarn(
  @NotNull String name,
  @NotNull SourcePos sourcePos
) implements Problem.Warn {
  @Override
  public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The name being defined `"),
      Doc.plain(name),
      Doc.plain("` introduces ambiguity. "),
      Doc.plain("It can only be accessed through a qualified name.")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }
}
