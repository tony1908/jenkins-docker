const assert = require("node:assert/strict");
const { message } = require("../src");

assert.equal(message(), "Built from SVN");
assert.equal(message("Jenkins"), "Built from Jenkins");

console.log("SVN app tests passed");
