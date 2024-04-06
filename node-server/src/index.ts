import http from 'http';

// @ts-ignore
import express, {Express} from 'express';

const router: Express = express();
const httpServer = http.createServer(router);

router.get('/', (req, res) => {
    res.send('Hello, TypeScript with Express!');
    console.info("got from " + req.url);
});

router.use((req: express.Request, res: express.Response) => {
    console.info("Metrics collect error, url " + req.url);
    const error = new Error('not found: ' + req.url);
    return res.status(404).json({
        message: error.message
    });
});

router.listen(3000, () => {
    console.log(`Server is running on http://localhost:${3000}`);
});