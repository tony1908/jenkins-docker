function message(source = "SVN") {
  return `Built from ${source}`;
}

if (require.main === module) {
  console.log(message("SVN commit"));
}

module.exports = { message };
