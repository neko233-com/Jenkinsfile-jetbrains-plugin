export function isJenkinsfilePath(fileName: string): boolean {
  const basename = fileName.split(/[\\/]/).at(-1) ?? fileName;
  return basename.toLocaleLowerCase("en-US").includes("jenkinsfile");
}
