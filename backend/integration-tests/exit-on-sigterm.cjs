// The Functions emulator stops each function worker with SIGTERM. Node writes no V8 coverage
// for a process that a signal ends, so c8 lost the coverage of each worker stopped that way,
// and the total changed from run to run. A worker that exits by itself writes its coverage.
if (/functionsEmulatorRuntime(\.js)?$/.test(process.argv[1] ?? "")) {
  process.on("SIGTERM", () => process.exit(143));
}
