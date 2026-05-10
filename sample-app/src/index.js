function greeting(name = "Jenkins") {
  return `Hello from ${name}`;
}

if (require.main === module) {
  console.log(greeting("Docker-in-Docker"));
}

module.exports = { greeting };
