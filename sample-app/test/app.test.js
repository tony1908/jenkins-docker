const assert = require("node:assert/strict");
const { greeting } = require("../src");

assert.equal(greeting(), "Hello from Jenkins");
assert.equal(greeting("Docker"), "Hello from Docker");

console.log("All tests passed");
