import * as vscode from "vscode";
import { provideCompletions } from "./completions.js";
import { collectDiagnostics } from "./diagnostics.js";
import { formatJenkinsfile } from "./formatter.js";
import { isJenkinsfilePath } from "./filename.js";
import { provideDocumentSymbols } from "./symbols.js";

export function activate(context: vscode.ExtensionContext): void {
  const diagnostics = vscode.languages.createDiagnosticCollection("jenkinsfile-free");
  context.subscriptions.push(diagnostics);

  const formattingProvider: vscode.DocumentFormattingEditProvider = {
    provideDocumentFormattingEdits(document, options) {
      if (!isJenkinsfileDocument(document)) return [];
      const current = document.getText();
      const formatted = formatJenkinsfile(current, {
        insertSpaces: options.insertSpaces, tabSize: options.tabSize,
        printWidth: vscode.workspace.getConfiguration("jenkinsfileFree", document).get<number>("format.printWidth", 100)
      });
      if (formatted === current) return [];
      return [vscode.TextEdit.replace(new vscode.Range(document.positionAt(0), document.positionAt(current.length)), formatted)];
    }
  };

  context.subscriptions.push(
    vscode.languages.registerDocumentFormattingEditProvider({ language: "jenkinsfile" }, formattingProvider),
    vscode.languages.registerCompletionItemProvider({ language: "jenkinsfile" }, {
      provideCompletionItems(document, position) {
        return provideCompletions(document, position);
      }
    }, ".", ":"),
    vscode.languages.registerDocumentSymbolProvider({ language: "jenkinsfile" }, {
      provideDocumentSymbols(document) {
        return provideDocumentSymbols(document);
      }
    }),
    vscode.commands.registerTextEditorCommand("jenkinsfileFree.formatDocument", async (editor) => {
      if (!isJenkinsfileDocument(editor.document)) {
        void vscode.window.showInformationMessage("Open a file whose name contains Jenkinsfile to format it.");
        return;
      }
      const current = editor.document.getText();
      const formatted = formatJenkinsfile(current, {
        insertSpaces: editor.options.insertSpaces !== false,
        tabSize: typeof editor.options.tabSize === "number" ? editor.options.tabSize : 4,
        printWidth: vscode.workspace.getConfiguration("jenkinsfileFree", editor.document).get<number>("format.printWidth", 100)
      });
      if (formatted === current) return;
      const edit = new vscode.WorkspaceEdit();
      edit.replace(editor.document.uri, new vscode.Range(editor.document.positionAt(0), editor.document.positionAt(current.length)), formatted);
      await vscode.workspace.applyEdit(edit);
    })
  );

  const refresh = (document: vscode.TextDocument): void => {
    if (!isJenkinsfileDocument(document)) return;
    diagnostics.set(document.uri, collectDiagnostics(document));
  };
  const associate = (document: vscode.TextDocument): void => {
    if (!isJenkinsfileDocument(document)) return;
    if (document.languageId !== "jenkinsfile") {
      void vscode.languages.setTextDocumentLanguage(document, "jenkinsfile").then(refresh);
    } else {
      refresh(document);
    }
  };

  for (const document of vscode.workspace.textDocuments) associate(document);
  context.subscriptions.push(
    vscode.workspace.onDidOpenTextDocument(associate),
    vscode.workspace.onDidChangeTextDocument((event) => refresh(event.document)),
    vscode.workspace.onDidSaveTextDocument(refresh),
    vscode.workspace.onDidCloseTextDocument((document) => diagnostics.delete(document.uri))
  );
}

export function deactivate(): void {
  // All registrations are owned by the extension context.
}

function isJenkinsfileDocument(document: vscode.TextDocument): boolean {
  return isJenkinsfilePath(document.uri.path);
}
