import { stitch } from '@google/stitch-sdk';

async function main() {
  try {
    const result = await stitch.callTool('get_screen', {
      projectId: '1702042826875269585',
      screenId: 'ba695899842447a48d37e1861420f808'
    });
    console.log(JSON.stringify(result, null, 2));
  } catch (e) {
    console.error('Error calling tool:', e.message || e);
  } finally {
    try {
      await stitch.close();
    } catch (e) {}
  }
}

main();
