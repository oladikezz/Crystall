const mineflayer = require('mineflayer');

const HOST = 'localhost';
const PORT = 25565;
const BOT_COUNT = 50; // Сколько ботов запустить
const DELAY_BETWEEN_JOINS = 500; // Пауза (мс) между входами, чтобы не положить сеть

let bots = [];

function createBot(id) {
    const bot = mineflayer.createBot({
        host: HOST,
        port: PORT,
        username: `StressBot_${id}`,
        version: '1.20.4'
    });

    bot.once('spawn', () => {
        console.log(`[+] ${bot.username} заспавнился!`);
        
        // Каждые 5-15 секунд бот будет менять направление или прыгать (имитация активности)
        setInterval(() => {
            const actions = ['forward', 'back', 'left', 'right', 'jump'];
            const randomAction = actions[Math.floor(Math.random() * actions.length)];
            
            // Сбрасываем старые состояния
            bot.clearControlStates();
            bot.setControlState(randomAction, true);
            
            // Если бот долго идет, останавливаем
            setTimeout(() => {
                bot.clearControlStates();
            }, 2000);
            
        }, 5000 + Math.random() * 10000);

        // Периодический спам в чат (каждые 30-60 сек)
        setInterval(() => {
            bot.chat(`Всем привет, я бот ${id} и я нагружаю сервер!`);
        }, 30000 + Math.random() * 30000);
    });

    bot.on('error', (err) => {
        console.log(`[-] Ошибка у ${bot.username}: ${err.message}`);
    });

    bot.on('end', () => {
        console.log(`[-] ${bot.username} отключился.`);
    });

    bots.push(bot);
}

// Запускаем спавн с задержкой
console.log(`=== Запуск стресс-теста на ${BOT_COUNT} ботов ===`);
for (let i = 0; i < BOT_COUNT; i++) {
    setTimeout(() => {
        createBot(i);
    }, i * DELAY_BETWEEN_JOINS);
}
