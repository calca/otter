const { Stitch } = require('@google/stitch-sdk');
const stitch = new Stitch();

async function main() {
  try {
    const screen = await stitch.getScreen('1702042826875269585', 'ba695899842447a48d37e1861420f808');
    console.log(JSON.stringify(screen, null, 2));
  } catch (e) {
    console.error(e);
  }
}

main();
