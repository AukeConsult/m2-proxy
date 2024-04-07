import http from 'http';

// @ts-ignore
import express, {Express} from 'express';

const router: Express = express();
const httpServer = http.createServer(router);

httpServer.keepAliveTimeout = 30000;
httpServer.maxConnections = 10;
httpServer.timeout = 30000;

router.get('/test', (req, res) => {
    console.info("url " + req.url +" " + Date.now().toString());
    res.shouldKeepAlive=true
    res.status(200).send('<p>Hello'+req.url+" " + Date.now().toString() + "</p>");
});

router.get('/json', (req, res) => {
    res.status(200).send(
        {hello: "leif",
        url: req.url,
        host: req.hostname,
        baseurl: req.baseUrl,
        agent: req.header('user-agent')
        }
    );
});

router.use((req: express.Request, res: express.Response) => {
    console.info("url " + req.url +" " + Date.now().toString());
    res.shouldKeepAlive=true
    res.send('Hello'+req.url+" " + Date.now().toString());
});

router.listen(3000, () => {
    console.log(`Server is running on http://localhost:${3000}`);
});